package org.jetbrains.plugins.scala
package lang
package psi

import java.{util => ju}

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, _}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, _}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.BetterMonadicForSupport.Implicit0Binding
import org.jetbrains.plugins.scala.util.{SAMUtil, ScEquivalenceUtil}

import scala.annotation.tailrec
import scala.collection.{Seq, Set, mutable}
import scala.reflect.{ClassTag, NameTransformer}

/**
  * User: Alexander Podkhalyuzin
  */
object ScalaPsiUtil {

  //java has magic @PolymorphicSignature annotation in java.lang.invoke.MethodHandle
  def isJavaReflectPolymorphicSignature(expression: ScExpression): Boolean = expression match {
    case ScMethodCall(invoked, _) => Option(invoked.getReference) match {
      case Some(ResolvesTo(method: PsiMethod)) =>
        AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)
      case _ => false
    }
    case _ => false
  }

  def nameWithPrefixIfNeeded(c: PsiClass): String = {
    val qName = c.qualifiedName
    if (ScalaCodeStyleSettings.getInstance(c.getProject).hasImportWithPrefix(qName)) qName.split('.').takeRight(2).mkString(".")
    else c.name
  }

  def typeParamString(
    param:                ScTypeParam,
    withLowerUpperBounds: Boolean = true,
    withViewBounds:       Boolean = true,
    withContextBounds:    Boolean = true
  ): String = {
    var paramText = param.name

    if (param.typeParameters.nonEmpty) {
      paramText +=
        param.typeParameters
          .map(typeParamString(_, withLowerUpperBounds, withViewBounds, withContextBounds))
          .mkString("[", ", ", "]")
    }

    if (withLowerUpperBounds) {
      param.lowerTypeElement.foreach(tp => paramText = paramText + " >: " + tp.getText)
      param.upperTypeElement.foreach(tp => paramText = paramText + " <: " + tp.getText)
    }

    if (withViewBounds) {
      param.viewTypeElement.foreach(tp => paramText = paramText + " <% " + tp.getText)
    }

    if (withContextBounds) {
      param.contextBoundTypeElement.foreach(tp => paramText = paramText + " : " + tp.getText)
    }

    paramText
  }

  def debug(message: => String, logger: Logger) {
    if (logger.isDebugEnabled) {
      logger.debug(message)
    }
  }

  def functionArrow(implicit project: ProjectContext): String = {
    val useUnicode = project.project != null && ScalaCodeStyleSettings.getInstance(project).REPLACE_CASE_ARROW_WITH_UNICODE_CHAR
    if (useUnicode) ScalaTypedHandler.unicodeCaseArrow else "=>"
  }

  @tailrec
  def drvTemplate(elem: PsiElement): Option[ScTemplateDefinition] = {
    val template = PsiTreeUtil.getContextOfType(elem, true, classOf[ScTemplateDefinition])
    if (template == null) return None
    template.extendsBlock.templateParents match {
      case Some(parents) if PsiTreeUtil.isContextAncestor(parents, elem, true) => drvTemplate(template)
      case _ => Some(template)
    }
  }

  @tailrec
  def firstLeaf(elem: PsiElement): PsiElement = {
    val firstChild: PsiElement = elem.getFirstChild
    if (firstChild == null) return elem
    firstLeaf(firstChild)
  }

  @tailrec
  def withEtaExpansion(expr: ScExpression): Boolean = {
    expr.getContext match {
      case _: ScMethodCall => false
      case g: ScGenericCall => withEtaExpansion(g)
      case p: ScParenthesisedExpr => withEtaExpansion(p)
      case _ => true
    }
  }

  def isInheritorDeep(clazz: PsiClass, base: PsiClass): Boolean = clazz.isInheritor(base, true)

  @tailrec
  def fileContext(psi: PsiElement): PsiFile = {
    if (psi == null) return null
    psi match {
      case f: PsiFile => f
      case _ => fileContext(psi.getContext)
    }
  }

  /**
    * If `s` is an empty sequence, (), otherwise TupleN(s0, ..., sn))
    *
    * See SCL-2001, SCL-3485
    */
  def tupled(s: Seq[Expression], context: PsiElement): Option[Seq[Expression]] = {
    implicit val scope: ElementScope = context.elementScope

    val maybeType = s match {
      case Seq() => Some(Unit)
      // object A { def foo(a: Any) = ()}; A foo () ==>> A.foo(()), or A.foo() ==>> A.foo( () )
      case _ =>
        def getType(expression: Expression) = {
          val result =
            expression
              .getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)
              .tr
          result.getOrAny
        }

        TupleType(s.map(getType)) match {
          case t if t.isNothing => None
          case t => Some(t)
        }
    }

    maybeType.map { t =>
      Seq(Expression(t, firstLeaf(context)))
    }
  }

  def getNextSiblingOfType[T <: PsiElement](sibling: PsiElement, aClass: Class[T]): T = {
    if (sibling == null) return null.asInstanceOf[T]
    var child: PsiElement = sibling.getNextSibling
    while (child != null) {
      if (aClass.isInstance(child)) {
        return child.asInstanceOf[T]
      }
      child = child.getNextSibling
    }
    null.asInstanceOf[T]
  }

  def processImportLastParent(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement,
                              lastParent: PsiElement, typeResult: => TypeResult): Boolean = {
    lastParent match {
      case _: ScImportStmt =>
        typeResult match {
          case Right(t) =>
            (processor, place) match {
              case (b: BaseProcessor, p: ScalaPsiElement) => b.processType(state.substitutor(t), p, state)
              case _ => true
            }
          case _ => true
        }
      case _ => true
    }
  }

  /**
    * Pick all type parameters by method maps them to the appropriate type arguments, if they are
    */
  def undefineMethodTypeParams(fun: PsiMethod): ScSubstitutor = {
    implicit val ctx: ProjectContext = fun
    val typeParameters = fun match {
      case fun: ScFunction => fun.typeParameters
      case fun: PsiMethod => fun.getTypeParameters.toSeq
    }
    ScSubstitutor.bind(typeParameters.map(TypeParameter(_)))(UndefinedType(_, level = 1))
  }

  @tailrec
  def isLocalOrPrivate(elem: PsiElement): Boolean = {
    elem.getContext match {
      case _: ScPackageLike | _: ScalaFile | _: ScEarlyDefinitions => false
      case _: ScTemplateBody =>
        elem match {
          case mem: ScMember =>
            if (mem.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)) true
            else isLocalOrPrivate(mem.containingClass)
          case _ => false
        }
      case _ => true
    }
  }

  def processTypeForUpdateOrApplyCandidates(call: MethodInvocation, tp: ScType, isShape: Boolean,
                                             isDynamic: Boolean): Array[ScalaResolveResult] = {
    val applyOrUpdateInvocation = ApplyOrUpdateInvocation(call, tp, isDynamic)
    applyOrUpdateInvocation.collectCandidates(isShape)
  }

  /**
    * This method created for the following example:
    * {{{
    *   new HashMap + (1 -> 2)
    * }}}
    * Method + has lower bound, which is second generic parameter of HashMap.
    * In this case new HashMap should create HashMap[Int, Nothing], then we can invoke + method.
    * However we can't use information from not inferred generic. So if such method use bounds on
    * not inferred generics, such bounds should be removed.
    */
  def removeBadBounds(tp: ScType): ScType = {
    import tp.projectContext

    tp match {
      case tp@ScTypePolymorphicType(internal, typeParameters) =>
        def hasBadLinks(tp: ScType, ownerPtp: PsiTypeParameter): Option[ScType] = {
          var res: Option[ScType] = Some(tp)
          tp.visitRecursively {
            case t: TypeParameterType =>
              if (typeParameters.exists {
                case TypeParameter(ptp, _, _, _) if ptp == t.psiTypeParameter && ptp.getOwner != ownerPtp.getOwner => true
                case _ => false
              }) res = None
            case _ =>
          }
          res
        }

        def clearBadLinks(tps: Seq[TypeParameter]): Seq[TypeParameter] = tps.map {
          case TypeParameter(psiTypeParameter, parameters, lowerType, upperType) =>
            TypeParameter(
              psiTypeParameter,
              clearBadLinks(parameters),
              hasBadLinks(lowerType, psiTypeParameter).getOrElse(Nothing),
              hasBadLinks(upperType, psiTypeParameter).getOrElse(Any))
        }

        ScTypePolymorphicType(internal, clearBadLinks(typeParameters))
      case _ => tp
    }
  }

  @tailrec
  def isAnonymousExpression(expr: ScExpression): (Int, ScExpression) = {
    val seq = ScUnderScoreSectionUtil.underscores(expr)
    if (seq.nonEmpty) return (seq.length, expr)
    expr match {
      case b: ScBlockExpr =>
        if (b.statements.length != 1) (-1, expr)
        else if (b.resultExpression.isEmpty) (-1, expr)
        else isAnonymousExpression(b.resultExpression.get)
      case p: ScParenthesisedExpr => p.innerElement match {
        case Some(x) => isAnonymousExpression(x)
        case _ => (-1, expr)
      }
      case f: ScFunctionExpr => (f.parameters.length, expr)
      case _ => (-1, expr)
    }
  }

  def isAnonExpression(expr: ScExpression): Boolean = isAnonymousExpression(expr)._1 >= 0

  def getModule(element: PsiElement): Module =
    element.getContainingFile.getVirtualFile match {
      case null => null
      case vFile =>
        val index: ProjectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
        index.getModuleForFile(vFile)
    }

  def parentPackage(packageFqn: String, project: Project): Option[ScPackageImpl] = {
    if (packageFqn.length == 0) None
    else {
      val lastDot: Int = packageFqn.lastIndexOf('.')
      val name =
        if (lastDot < 0) ""
        else packageFqn.substring(0, lastDot)
      val psiPack = ScalaPsiManager.instance(project).getCachedPackage(name).orNull
      Option(ScPackageImpl(psiPack))
    }
  }

  /**
    * This method doesn't collect things like
    * val a: Type = b //after implicit convesion
    * Main task for this method to collect imports used in 'for' statemnts.
    */
  def getExprImports(z: ScExpression): Set[ImportUsed] = {
    var res: Set[ImportUsed] = Set.empty
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitExpression(expr: ScExpression) {
        //Implicit parameters
        expr.findImplicitArguments match {
          case Some(results) => for (r <- results if r != null) res = res ++ r.importsUsed
          case _ =>
        }

        //implicit conversions
        def addConversions(fromUnderscore: Boolean) {
          res = res ++ expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType(fromUnderscore),
            fromUnderscore = fromUnderscore).importsUsed
        }

        if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) addConversions(fromUnderscore = true)
        addConversions(fromUnderscore = false)

        expr match {
          case f: ScFor =>
            f.desugared() match {
              case Some(e) => res = res ++ getExprImports(e)
              case _ =>
            }
          case call: ScMethodCall =>
            res = res ++ call.getImportsUsed
            super.visitExpression(expr)
          case ref: ScReferenceExpression =>
            res ++= ref.multiResolveScala(false).flatMap(_.importsUsed)
            super.visitExpression(expr)
          case _ =>
            super.visitExpression(expr)
        }
      }
    }
    z.accept(visitor)
    res
  }

  def getSettings(project: Project): ScalaCodeStyleSettings = {
    CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
  }

  def getElementsRange(start: PsiElement, end: PsiElement): Seq[PsiElement] = {
    if (start == null || end == null) return Nil
    val file = start.getContainingFile
    if (file == null || file != end.getContainingFile) return Nil

    val commonParent = PsiTreeUtil.findCommonParent(start, end)
    val startOffset = start.getTextRange.getStartOffset
    val endOffset = end.getTextRange.getEndOffset
    if (commonParent.getTextRange.getStartOffset == startOffset &&
      commonParent.getTextRange.getEndOffset == endOffset) {
      var parent = commonParent.getParent
      var prev = commonParent
      if (parent == null || parent.getTextRange == null) return Seq(prev)
      while (parent.getTextRange.equalsToRange(prev.getTextRange.getStartOffset, prev.getTextRange.getEndOffset)) {
        prev = parent
        parent = parent.getParent
        if (parent == null || parent.getTextRange == null) return Seq(prev)
      }
      return Seq(prev)
    }
    val buffer = mutable.ArrayBuffer.empty[PsiElement]
    var child = commonParent.getNode.getFirstChildNode
    var writeBuffer = false
    while (child != null) {
      if (child.getTextRange.getStartOffset == startOffset) {
        writeBuffer = true
      }
      if (writeBuffer) buffer += child.getPsi
      if (child.getTextRange.getEndOffset >= endOffset) {
        writeBuffer = false
      }
      child = child.getTreeNext
    }
    buffer
  }


  /**
   * @return maximal element of specified type starting at startOffset exactly and ending not farther than endOffset
   *         May return several elements if they have exactly same range.
   */
  def elementsAtRange[T <: PsiElement : ClassTag](file: PsiFile, startOffset: Int, endOffset: Int): Seq[T] = {
    def fit(e: PsiElement): Boolean =
      e != null && e.startOffset == startOffset && e.endOffset <= endOffset

    val startElem = file.findElementAt(startOffset)
    val allInRange = startElem.withParentsInFile.takeWhile(fit).toList.filterBy[T]
    if (allInRange.isEmpty) Seq.empty
    else {
      val maxEndOffset = allInRange.map(_.endOffset).max
      allInRange.filter(_.endOffset == maxEndOffset)
    }
  }

  /*
   * Adjusts the element to the previous element if the offset could also point to the previous element
   *
   * identifier<caret><ws> -> return identifier
   * identifier<ws1><caret><ws2> -> return ws2
   * +<caret>identifier -> return identifier
   * identifier<caret>+ -> return identifier iff preferIdentifier else return +
   *
   */
  def adjustElementAtOffset(element: PsiElement, offset: Int, preferIdentifier: Boolean = false): PsiElement = {
    if (offset != element.getTextOffset)
      return element

    val prev = PsiTreeUtil.prevLeaf(element)
    if (prev == null || prev.is[PsiWhiteSpace])
      return element

    element match {
      case _: PsiWhiteSpace =>
        prev
      case _ if preferIdentifier && prev.elementType == ScalaTokenTypes.tIDENTIFIER =>
        prev
      case _ =>
        element
    }
  }

  def strictlyOrderedByContext(before: PsiElement, after: PsiElement, topLevel: Option[PsiElement]): Boolean = {
    if (before == after) return false

    val contexts = getContexts(before, topLevel.orNull) zip getContexts(after, topLevel.orNull)
    val firstDifference = contexts.find { case (a, b) => a != b }

    firstDifference.exists {
      case (beforeAncestor, afterAncestor) =>
        !afterAncestor
          .withNextStubOrAstContextSiblings
          .contains(beforeAncestor.sameElementInContext)
    }
  }

  @tailrec
  def getContexts(elem: PsiElement, stopAt: PsiElement, acc: List[PsiElement] = Nil): List[PsiElement] = {
    elem.getContext match {
      case null | `stopAt` => elem :: acc
      case context => getContexts(context, stopAt, context :: acc)
    }
  }

  @tailrec
  def getParents(elem: PsiElement, stopAt: PsiElement, acc: List[PsiElement] = Nil): List[PsiElement] = {
    elem.getParent match {
      case null | `stopAt` => acc
      case parent => getParents(parent, stopAt, parent :: acc)
    }
  }

  def getStubOrPsiSibling(element: PsiElement, next: Boolean = false): PsiElement = {
    val container = for {
      stub <- stub(element)
      parent <- stub.getParentStub.nullSafe

      childrenStubs = parent.getChildrenStubs
      maybeElement = childrenStubs.indexOf(stub) match {
        case -1 => null
        case index => at(childrenStubs)(index + (if (next) 1 else -1))
      }
    } yield maybeElement

    container.fold(if (next) element.getNextSibling else element.getPrevSibling)(_.orNull)
  }

  def at(stubs: ju.List[StubElement[_ <: PsiElement]])
        (index: Int = stubs.size - 1): Option[PsiElement] =
    if (index < 0 || index >= stubs.size) None
    else Some(stubs.get(index).getPsi)

  def stub(element: PsiElement): NullSafe[StubElement[_]] = NullSafe {
    element match {
      case stubbed: StubBasedPsiElementBase[_] => stubbed.getStub
      case file: PsiFileImpl => file.getStub
      case _ => null
    }
  }

  def isLValue(elem: PsiElement): Boolean = elem match {
    case e: ScExpression => e.getParent match {
      case as: ScAssignment => as.leftExpression eq e
      case _ => false
    }
    case _ => false
  }

  def getPlaceTd(placer: PsiElement, ignoreTemplateParents: Boolean = false): ScTemplateDefinition = {
    val td = PsiTreeUtil.getContextOfType(placer, true, classOf[ScTemplateDefinition])
    if (ignoreTemplateParents) return td
    if (td == null) return null
    val res = td.extendsBlock.templateParents match {
      case Some(parents) =>
        if (PsiTreeUtil.isContextAncestor(parents, placer, true)) getPlaceTd(td)
        else td
      case _ => td
    }
    res
  }

  @tailrec
  def isPlaceTdAncestor(td: ScTemplateDefinition, placer: PsiElement): Boolean = {
    val newTd = getPlaceTd(placer)
    if (newTd == null) return false
    if (newTd == td) return true
    isPlaceTdAncestor(td, newTd)
  }

  def namedElementSig(x: PsiNamedElement): TermSignature =
    TermSignature(x.name, Seq.empty, ScSubstitutor.empty, x)

  def superValsSignatures(x: PsiNamedElement, withSelfType: Boolean = false): Seq[TermSignature] = {
    val empty = Seq.empty
    val typed: ScTypedDefinition = x match {
      case x: ScTypedDefinition => x
      case _ => return empty
    }
    val clazz: ScTemplateDefinition = typed.nameContext match {
      case e@(_: ScValue | _: ScVariable | _: ScObject) if e.getParent.isInstanceOf[ScTemplateBody] ||
        e.getParent.isInstanceOf[ScEarlyDefinitions] =>
        e.asInstanceOf[ScMember].containingClass
      case e: ScClassParameter if e.isClassMember => e.containingClass
      case _ => return empty
    }
    if (clazz == null) return empty
    val s = namedElementSig(x)
    val signatures =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(clazz)
      else TypeDefinitionMembers.getSignatures(clazz)
    val sigs = signatures.forName(x.name)
    var res: Seq[TermSignature] = (sigs.get(s): @unchecked) match {
      //partial match
      case Some(node) if !withSelfType || node.info.namedElement == x => node.supers.map {
        _.info
      }
      case Some(node) =>
        node.supers.map {
          _.info
        }.filter {
          _.namedElement != x
        } :+ node.info
      case None =>
        //this is possible case: private member of library source class.
        //Problem is that we are building signatures over decompiled class.
        Seq.empty
    }


    val beanMethods = getBeanMethods(typed)
    beanMethods.foreach {
      method =>
        val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)
        (sigs.get(new PhysicalMethodSignature(method, ScSubstitutor.empty)): @unchecked) match {
          //partial match
          case Some(node) if !withSelfType || node.info.namedElement == method => res ++= node.supers.map {
            _.info
          }
          case Some(node) =>
            res +:= node.info
            res ++= node.supers.map {
              _.info
            }.filter {
              _.namedElement != method
            }
          case None =>
        }
    }

    res

  }

  def superTypeMembers(element: PsiNamedElement,
                       withSelfType: Boolean = false): Seq[PsiNamedElement] = {

    superTypeSignatures(element, withSelfType).map(_.namedElement)
  }

  def superTypeSignatures(element: PsiNamedElement,
                          withSelfType: Boolean = false): Seq[TypeSignature] = {

    val clazz: ScTemplateDefinition = element.nameContext match {
      case e@(_: ScTypeAlias | _: ScTrait | _: ScClass) if e.getParent.isInstanceOf[ScTemplateBody] => e.asInstanceOf[ScMember].containingClass
      case _ => return Seq.empty
    }
    val types =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(clazz)
      else TypeDefinitionMembers.getTypes(clazz)

    types.forName(element.name).findNode(element) match {
      //partial match
      case Some(x) if !withSelfType || x.info == element => x.supers.map(_.info)
      case Some(x) =>
        x.supers.map(_.info).filter(_ != element) :+ x.info
      case None =>
        //this is possible case: private member of library source class.
        //Problem is that we are building types over decompiled class.
        Seq.empty
    }
  }

  def isNameContext(x: PsiElement): Boolean = x match {
    case _: ScMember | _: ScParameter | _: ScCaseClause | _: ScPatterned => true
    case _: PsiClass | _: PsiMethod | _: PsiField | _: PsiPackage => true
    case _ => false
  }

  def nameContext(x: PsiNamedElement): PsiElement = x.nameContext

  object inNameContext {
    def unapply(x: PsiNamedElement): Option[PsiElement] = Option(x.nameContext)
  }

  def getEmptyModifierList(manager: PsiManager): PsiModifierList =
    new LightModifierList(manager, ScalaLanguage.INSTANCE) {
      override def hasModifierProperty(name: String): Boolean =
        if (name != "public") super.hasModifierProperty(name)
        else !super.hasModifierProperty("private") && !super.hasModifierProperty("protected")
    }

  def adjustTypes(element: PsiElement, addImports: Boolean = true, useTypeAliases: Boolean = true) {
    TypeAdjuster.adjustFor(Seq(element), addImports, useTypeAliases)
  }

  def getMethodPresentableText(method: PsiMethod, subst: ScSubstitutor = ScSubstitutor.empty): String = {
    method match {
      case method: ScFunction =>
        ScalaElementPresentation.getMethodPresentableText(method, fast = false, subst)
      case _ =>
        val PARAM_OPTIONS: Int = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER

        implicit val elementScope: ElementScope = method.elementScope
        PsiFormatUtil.formatMethod(method, getPsiSubstitutor(subst),
          PARAM_OPTIONS | PsiFormatUtilBase.SHOW_PARAMETERS, PARAM_OPTIONS)
    }
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => true
      case _ => false
    }
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalMethodSignature] = {
    val isObject = clazz.isInstanceOf[ScObject]
    TypeDefinitionMembers.getSignatures(clazz).forName("apply").iterator.collect {
      case p: PhysicalMethodSignature if isObject || p.method.hasModifierProperty("static") => p
    }.toList
  }

  @tailrec
  def getParentWithProperty(element: PsiElement, strict: Boolean, property: PsiElement => Boolean): Option[PsiElement] = {
    if (element == null) None
    else if (!strict && property(element)) Some(element)
    else getParentWithProperty(element.getParent, strict = false, property)
  }

  @tailrec
  def getParent(element: PsiElement, level: Int): Option[PsiElement] =
    if (level == 0) Some(element) else if (element.parent.isEmpty) None
    else getParent(element.getParent, level - 1)

  def contextOfType[T <: PsiElement](element: PsiElement, strict: Boolean, clazz: Class[T]): T = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null.asInstanceOf[T]
      element.getContext
    }
    while (el != null && !clazz.isInstance(el)) el = el.getContext
    el.asInstanceOf[T]
  }

  @tailrec
  def getContext(element: PsiElement, level: Int): Option[PsiElement] =
    if (level == 0) Some(element) else if (element.getContext == null) None
    else getContext(element.getParent, level - 1)

  /**
    * For one classOf use PsiTreeUtil.getContextOfType instead
    */
  def getContextOfType(element: PsiElement, strict: Boolean, classes: Class[_ <: PsiElement]*): PsiElement = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null
      element.getContext
    }
    while (el != null && !classes.exists(_.isInstance(el))) el = el.getContext
    el
  }

  def getCompanionModule(clazz: PsiClass): Option[ScTypeDefinition] = clazz match {
    case obj: ScObject if obj.isSyntheticObject => obj.syntheticNavigationElement.asOptionOf[ScTypeDefinition]
    case obj: ScObject                          => obj.baseCompanionModule
    case definition: ScTypeDefinition           => definition.baseCompanionModule.orElse(definition.fakeCompanionModule)
    case _                                      => None
  }

  // determines if an element can access other elements in a synthetic subtree that shadows
  // element's original(physical) subtree - e.g. physical method of some class referencing
  // a synthetic method of the same class
  def isSyntheticContextAncestor(ancestor: PsiElement, element: PsiElement): Boolean = {
    ancestor.getContext match {
      case td: ScTemplateDefinition if td.isDesugared =>
        PsiTreeUtil.isContextAncestor(td.originalElement.get, element, true)
      case _ => false
    }
  }

  object FakeCompanionClassOrCompanionClass {
    def unapply(obj: ScObject): Option[PsiClass] = Option(obj.fakeCompanionClassOrCompanionClass)
  }

  def isStaticJava(m: PsiMember): Boolean = m match {
    case null => false
    case _ if !m.getLanguage.isInstanceOf[JavaLanguage] => false
    case _: PsiEnumConstant => true
    case cl: PsiClass if cl.isInterface | cl.isEnum => true
    case m: PsiMember if m.hasModifierPropertyScala(PsiModifier.STATIC) => true
    case f: PsiField if f.containingClass.isInterface => true
    case _ => false
  }

  def hasStablePath(o: PsiNamedElement): Boolean = {
    o.nameContext match {
      case member: PsiMember => isStatic(member)
      case _: ScPackaging | _: PsiPackage => true
      case _ => false
    }
  }

  @tailrec
  final def isStatic(m: PsiMember): Boolean = {
    m.getContext match {
      case _: PsiFile => return true
      case _: ScPackaging | _: PsiPackage => return true
      case _ =>
    }
    m.containingClass match {
      case null => false
      case o: ScObject if o.isPackageObject || o.qualifiedName == "scala.Predef" => true
      case o: ScObject => isStatic(o)
      case j if isStaticJava(m) => isStatic(j)
      case _ => false
    }
  }


  def getPsiSubstitutor(subst: ScSubstitutor)
                       (implicit elementScope: ElementScope): PsiSubstitutor = {

    case class PseudoPsiSubstitutor(substitutor: ScSubstitutor) extends PsiSubstitutor {
      def putAll(parentClass: PsiClass, mappings: Array[PsiType]): PsiSubstitutor = PsiSubstitutor.EMPTY

      def isValid: Boolean = true

      def put(classParameter: PsiTypeParameter, mapping: PsiType): PsiSubstitutor = PsiSubstitutor.EMPTY

      def getSubstitutionMap: ju.Map[PsiTypeParameter, PsiType] = new ju.HashMap[PsiTypeParameter, PsiType]()

      def substitute(`type`: PsiType): PsiType = {
        substitutor(`type`.toScType()).toPsiType
      }

      def substitute(typeParameter: PsiTypeParameter): PsiType = {
        substitutor(TypeParameterType(typeParameter)).toPsiType
      }

      def putAll(another: PsiSubstitutor): PsiSubstitutor = PsiSubstitutor.EMPTY

      def substituteWithBoundsPromotion(typeParameter: PsiTypeParameter): PsiType = substitute(typeParameter)

      def ensureValid() {}
    }

    PseudoPsiSubstitutor(subst)
  }

  @tailrec
  def newLinesEnabled(element: PsiElement): Boolean = {
    if (element == null) true
    else {
      element.getParent match {
        case block: ScBlock if block.hasRBrace => true
        case _: ScMatch | _: ScalaFile | null => true
        case argList: ScArgumentExprList if argList.isBraceArgs => true

        case argList: ScArgumentExprList if !argList.isBraceArgs => false
        case _: ScParenthesisedExpr | _: ScTuple |
             _: ScTypeArgs | _: ScPatternArgumentList |
             _: ScParameterClause | _: ScTypeParamClause => false
        case caseClause: ScCaseClause =>

          import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

          val funTypeToken = caseClause.findLastChildByType(TokenSet.create(tFUNTYPE, tFUNTYPE_ASCII))
          if (funTypeToken != null && element.getTextOffset < funTypeToken.getTextOffset) false
          else newLinesEnabled(caseClause.getParent)

        case other => newLinesEnabled(other.getParent)
      }
    }
  }

  /*
  ******** any subexpression of these does not need parentheses **********
  * ScTuple, ScBlock, ScXmlExpr
  *
  ******** do not need parentheses with any parent ***************
  * ScReferenceExpression, ScMethodCall,
  * ScGenericCall, ScLiteral, ScTuple,
  * ScXmlExpr, ScParenthesisedExpr, ScUnitExpr
  * ScThisReference, ScSuperReference
  *
  * *********
  * ScTuple in ScInfixExpr should be treated specially because of auto-tupling
  * Underscore functions in sugar calls and reference expressions do need parentheses
  * ScMatchStmt in ScGuard do need parentheses
  *
  ********** other cases (1 - need parentheses, 0 - does not need parentheses *****
  *
  *		          \ Child       ScBlockExpr 	ScNewTemplateDefinition 	ScUnderscoreSection	  ScPrefixExpr	  ScInfixExpr	  ScPostfixExpr     Other
  *      Parent  \
  *   ScMethodCall	              1                   1	                        1	                1             1	              1        |    1
  *   ScUnderscoreSection	        1                   1	                        1	                1             1	              1        |    1
  *   ScGenericCall		            0                   1	                        1	                1             1	              1        |    1
  *   ScReferenceExpression			  0                special                      1	                1             1	              1        |    1
  *   ScPrefixExpr	              0                   0	                        0	                1             1	              1        |    1
  *   ScInfixExpr	                0                   0	                        0	                0          special            1        |    1
  *   ScPostfixExpr	              0                   0	                        0	                0             0	              1        |    1
  *   ScTypedStmt	                0                   0	                        0	                0             0	              0        |    1
  *   ScMatchStmt	                0                   0	                        0	                0             0	              0        |    1
	*		-----------------------------------------------------------------------------------------------------------------------------------
  *	  Other                       0                   0	                        0	                0             0	              0             0
  * */
  def needParentheses(from: ScExpression, expr: ScExpression): Boolean = {
    def infixInInfixParentheses(parent: ScInfixExpr, child: ScInfixExpr): Boolean = {

      import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr._
      import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._

      if (parent.left == from) {
        val lid = parent.operation.getText
        val rid = child.operation.getText
        if (priority(lid) < priority(rid)) true
        else if (priority(rid) < priority(lid)) false
        else if (associate(lid) != associate(rid)) true
        else if (associate(lid) == -1) true
        else false
      }
      else {
        val lid = child.operation.getText
        val rid = parent.operation.getText
        if (priority(lid) < priority(rid)) false
        else if (priority(rid) < priority(lid)) true
        else if (associate(lid) != associate(rid)) true
        else if (associate(lid) == -1) false
        else true
      }
    }

    def tupleInInfixNeedParentheses(parent: ScInfixExpr, from: ScExpression, expr: ScTuple): Boolean = {
      if (from.getParent != parent) throw new IllegalArgumentException
      if (from == parent.left) false
      else {
        parent.operation.bind() match {
          case Some(resolveResult) =>
            val startInParent: Int = from.getStartOffsetInParent
            val endInParent: Int = startInParent + from.getTextLength
            val parentText = parent.getText
            val modifiedParentText = parentText.substring(0, startInParent) + from.getText + parentText.substring(endInParent)
            val modifiedParent = createExpressionFromText(modifiedParentText, parent.getContext)
            modifiedParent match {
              case ScInfixExpr(_, newOper, _: ScTuple) =>
                newOper.bind() match {
                  case Some(newResolveResult) =>
                    newResolveResult.getElement == resolveResult.getElement && newResolveResult.tuplingUsed
                  case _ => true
                }
              case _ => true
            }
          case _ => false
        }
      }
    }

    def parsedDifferently(from: ScExpression, expr: ScExpression): Boolean = {
      if (newLinesEnabled(from)) {
        expr.getParent match {
          case ScParenthesisedExpr(_) =>
            val text = expr.getText
            val dummyFile = createScalaFileFromText(text)(expr.getManager)
            dummyFile.firstChild match {
              case Some(newExpr: ScExpression) => newExpr.getText != text
              case _ => true
            }
          case _ => false
        }
      } else false
    }

    if (parsedDifferently(from, expr)) true
    else {
      val parent = from.getParent
      (parent, expr) match {
        //order of these case clauses is important!
        case (_: ScGuard, _: ScMatch) => true
        case _ if !parent.isInstanceOf[ScExpression] => false
        case _ if expr.getText == "_" => false
        case (_: ScTuple | _: ScBlock | _: ScXmlExpr, _) => false
        case (infix: ScInfixExpr, tuple: ScTuple) => tupleInInfixNeedParentheses(infix, from, tuple)
        case (_: ScSugarCallExpr |
              _: ScReferenceExpression, elem: PsiElement) if ScUnderScoreSectionUtil.isUnderscoreFunction(elem) => true
        case (_, _: ScReferenceExpression | _: ScMethodCall |
                 _: ScGenericCall | _: ScLiteral | _: ScTuple |
                 _: ScXmlExpr | _: ScParenthesisedExpr | _: ScUnitExpr |
                 _: ScThisReference | _: ScSuperReference) => false
        case (_: ScMethodCall | _: ScUnderscoreSection, _) => true
        case (_, _: ScBlock) => false
        case (_: ScGenericCall, _) => true
        case (_: ScReferenceExpression, _: ScNewTemplateDefinition) =>
          val lastChar: Char = expr.getText.last
          lastChar != ')' && lastChar != '}' && lastChar != ']'
        case (_: ScReferenceExpression, _) => true
        case (_, _: ScNewTemplateDefinition |
                 _: ScUnderscoreSection) => false
        case (_: ScPrefixExpr, _) => true
        case (_, _: ScPrefixExpr) => false
        case (par: ScInfixExpr, child: ScInfixExpr) => infixInInfixParentheses(par, child)
        case (_, _: ScInfixExpr) => false
        case (_: ScPostfixExpr | _: ScInfixExpr, _) => true
        case (_, _: ScPostfixExpr) => false
        case (_: ScTypedExpression | _: ScMatch, _) => true
        case _ => false
      }
    }
  }

  def isScope(element: PsiElement): Boolean = element match {
    case worksheetFile: ScalaFile if worksheetFile.isWorksheetFile => false
    case multDeclAllowedFile: ScalaFile if multDeclAllowedFile.isMultipleDeclarationsAllowed => false
    case _: ScalaFile | _: ScBlock | _: ScTemplateBody | _: ScPackaging | _: ScParameters |
         _: ScTypeParamClause | _: ScCaseClause | _: ScFor | _: ScExistentialClause |
         _: ScEarlyDefinitions | _: ScRefinement => true
    case e: ScPatternDefinition if e.getContext.isInstanceOf[ScCaseClause] => true // {case a => val a = 1}
    case _ => false
  }

  def stringValueOf(e: PsiLiteral): Option[String] = e.getValue.toOption.flatMap(_.asOptionOf[String])

  def readAttribute(annotation: PsiAnnotation, name: String): Option[String] = {
    annotation.findAttributeValue(name) match {
      case literal: PsiLiteral => stringValueOf(literal)
      case element: ScReference => element.getReference.toOption
        .flatMap(_.resolve().asOptionOf[ScBindingPattern])
        .flatMap(_.getParent.asOptionOf[ScPatternList])
        .filter(_.simplePatterns)
        .flatMap(_.getParent.asOptionOf[ScPatternDefinition])
        .flatMap(_.expr.flatMap(_.asOptionOf[PsiLiteral]))
        .flatMap(stringValueOf)
      case _ => None
    }
  }

  /**
    * Finds the n-th parameter from the primiary constructor of `cls`
    */
  def nthConstructorParam(cls: ScClass, n: Int): Option[ScParameter] = cls.constructor match {
    case Some(x: ScPrimaryConstructor) =>
      val clauses = x.parameterList.clauses
      if (clauses.isEmpty) None
      else {
        val params = clauses.head.parameters
        if (params.length > n)
          Some(params(n))
        else
          None
      }
    case _ => None
  }

  /**
    * @return Some(parameter) if the expression is an argument expression that can be resolved to a corresponding
    *         parameter; None otherwise.
    */
  @tailrec
  def parameterOf(exp: ScExpression): Option[Parameter] = {
    def fromMatchedParams(matched: Seq[(ScExpression, Parameter)]) = {
      matched.collectFirst {
        case (e, p) if e == exp => p
      }
    }

    exp match {
      case ScAssignment(ResolvesTo(p: ScParameter), Some(_)) => Some(Parameter(p))
      case _ =>
        exp.getParent match {
          case parenth: ScParenthesisedExpr => parameterOf(parenth)
          case named: ScAssignment => parameterOf(named)
          case block: ScBlock if block.statements == Seq(exp) => parameterOf(block)
          case ScInfixExpr.withAssoc(_, ResolvesTo(method: PsiMethod), `exp`) =>
            (method match {
              case function: ScFunction => function.parameters
              case _ => method.parameters
            }).headOption.map(Parameter(_))
          case (_: ScTuple) childOf (inf: ScInfixExpr) => fromMatchedParams(inf.matchedParameters)
          case args: ScArgumentExprList => fromMatchedParams(args.matchedParameters)
          case _ => None
        }
    }
  }

  /**
    * If `param` is a synthetic parameter with a corresponding real parameter, return Some(realParameter), otherwise None
    */
  def parameterForSyntheticParameter(param: ScParameter): Option[ScParameter] =
    param.parentOfType(classOf[ScFunction])
      .filter(_.isSynthetic)
      .flatMap {
        case fun if fun.isCopyMethod => Option(fun.containingClass)
        case fun if fun.isApplyMethod => getCompanionModule(fun.containingClass)
      case _ => None
    }.collect {
      case td: ScClass if td.isCase => td
    }.flatMap(_.constructor).toSeq
      .flatMap(_.parameters)
      .find(_.name == param.name) // TODO multiple parameter sections.

  def isReadonly(e: PsiElement): Boolean = {
    e match {
      case classParameter: ScClassParameter =>
        return classParameter.isVal
      case _ =>
    }

    if (e.isInstanceOf[ScParameter]) {
      return true
    }

    val parent = e.getParent

    if (parent.isInstanceOf[ScGenerator] ||
      parent.isInstanceOf[ScForBinding] ||
      parent.isInstanceOf[ScCaseClause]) {
      return true
    }

    e.parentsInFile.takeWhile(!isScope(_)).containsInstanceOf[ScPatternDefinition]
  }

  private def isCanonicalArg(expr: ScExpression) = expr match {
    case _: ScParenthesisedExpr => false
    case ScBlock(_: ScExpression) => false
    case _ => true
  }

  def isByNameArgument(expr: ScExpression): Boolean =
    isCanonicalArg(expr) && parameterOf(expr).exists(_.isByName)

  def isArgumentOfFunctionType(expr: ScExpression): Boolean =
    isCanonicalArg(expr) && parameterOf(expr).exists(p => FunctionType.isFunctionType(p.paramType))

  object MethodValue {
    def unapply(expr: ScExpression): Option[PsiMethod] =
      if (!isPossibleByClass(expr) || !functionOrSamTypeExpected(expr)) None
      else
        expr match {
          case ref: ScReferenceExpression if !ref.getParent.isInstanceOf[MethodInvocation] =>
            referencedMethod(ref, canBeParameterless = false)
          case gc: ScGenericCall if !gc.getParent.isInstanceOf[MethodInvocation] =>
            referencedMethod(gc, canBeParameterless = false)
          case us: ScUnderscoreSection =>
            us.bindingExpr.flatMap(referencedMethod(_, canBeParameterless = true))
          case ScMethodCall(
            invoked @ (_: ScReferenceExpression | _: ScGenericCall | _: ScMethodCall),
            args
          ) if args.nonEmpty && args.forall(isSimpleUnderscore) =>
            referencedMethod(invoked, canBeParameterless = false)
          case mc: ScMethodCall if !mc.getParent.isInstanceOf[ScMethodCall] =>
            referencedMethod(mc, canBeParameterless = false).filter {
              case f: ScFunction if f.paramClauses.clauses.size > numberOfArgumentClauses(mc) =>
                true
              case _ => false
            }
          case _ => None
        }

    private def cantBeEtaExpanded(m: ScFunctionDefinition): Boolean = {
      import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
      val clauses         = m.paramClauses.clauses
      lazy val isScala211 = m.scalaLanguageLevelOrDefault == Scala_2_11

      clauses.isEmpty ||
        clauses.forall { clause =>
          val isEmptyClause =
            if (isScala211) false
            else            clause.parameters.isEmpty

          clause.isImplicit || isEmptyClause
        }
    }

    @tailrec
    private def referencedMethod(
      expr:               ScExpression,
      canBeParameterless: Boolean
    ): Option[PsiMethod] = expr match {
      case ResolvesTo(f: ScFunctionDefinition) if cantBeEtaExpanded(f) && !canBeParameterless => None
      case ResolvesTo(m: PsiMethod)                                                           => Some(m)
      case gc: ScGenericCall =>
        referencedMethod(gc.referencedExpr, canBeParameterless)
      case us: ScUnderscoreSection if us.bindingExpr.isDefined =>
        referencedMethod(us.bindingExpr.get, canBeParameterless)
      case m: ScMethodCall => referencedMethod(m.deepestInvokedExpr, canBeParameterless = false)
      case _               => None
    }

    private def isSimpleUnderscore(expr: ScExpression) = expr match {
      case _: ScUnderscoreSection => expr.getText == "_"
      case typed: ScTypedExpression => Option(typed.expr).map(_.getText).contains("_")
      case _ => false
    }

    private def numberOfArgumentClauses(mc: ScMethodCall): Int = {
      mc.getEffectiveInvokedExpr match {
        case m: ScMethodCall => 1 + numberOfArgumentClauses(m)
        case _ => 1
      }
    }

    private def isPossibleByClass(expr: ScExpression): Boolean = expr match {
      case _: ScReferenceExpression |
           _: ScGenericCall |
           _: ScUnderscoreSection |
           _: ScMethodCall => true
      case _ => false
    }

    private def functionOrSamTypeExpected(expr: ScExpression): Boolean =
      expr.expectedType(fromUnderscore = false).exists {
        case FunctionType(_, _) => true
        case expected           => expr.isSAMEnabled && SAMUtil.toSAMType(expected, expr).isDefined
      }
  }

  def isConcreteElement(element: PsiElement): Boolean = {
    element match {
      case _: ScFunctionDefinition => true
      case f: ScFunctionDeclaration if f.isNative => true
      case _: ScFunctionDeclaration => false
      case _: ScFun => true
      case Constructor.ofClass(c) if c.isInterface => false
      case method: PsiMethod if !method.hasAbstractModifier && !method.isConstructor => true
      case method: PsiMethod if method.hasModifierProperty(PsiModifier.NATIVE) => true
      case _: ScPatternDefinition => true
      case _: ScVariableDefinition => true
      case _: ScClassParameter => true
      case _: ScTypeDefinition => true
      case _: ScTypeAliasDefinition => true
      case _ => false
    }
  }

  def isConcreteTermSignature(signature: TermSignature): Boolean = {
    val element = nameContext(signature.namedElement)
    isConcreteElement(element)
  }

  @annotation.tailrec
  private def simpleBoundName(bound: ScTypeElement): Option[String] = bound match {
    case ScSimpleTypeElement(ref)           => NameTransformer.encode(ref.refName).toOption
    case proj: ScTypeProjection             => NameTransformer.encode(proj.refName).toOption
    case ScInfixTypeElement(_, op, _)       => NameTransformer.encode(op.refName).toOption
    case ScParameterizedTypeElement(ref, _) => simpleBoundName(ref)
    case _                                  => None
  }

  private def contextBoundParameterName(
    typeParameter: ScTypeParam,
    typeElement:   ScTypeElement,
    index:         Int
  ): String = {
    val boundName = simpleBoundName(typeElement)
    val tpName    = NameTransformer.encode(typeParameter.name)

    def addIdSuffix(name: String): String = name + "$" + tpName + "$" + index

    lazy val fallbackName = "`" + addIdSuffix(typeElement.getText.replaceAll("\\s+", "")) + "`"

    boundName.fold(fallbackName)(sname =>
      StringUtil.decapitalize(sname + "$" + tpName + "$" + index)
    )
  }

  def withOriginalContextBound[T](parameter: ScParameter)
                                 (default: => T)
                                 (function: ((ScTypeParam, ScTypeElement, Int)) => T): T =
    if (parameter.isPhysical) default
    else {
      val maybeOwner = parameter.owner match {
        case ScPrimaryConstructor.ofClass(cls) => Option(cls)
        case other: ScTypeParametersOwner => Option(other)
        case _ => None
      }

      val bounds = for {
        owner <- maybeOwner.toSeq
        typeParameter <- owner.typeParameters
        (bound, idx) <- typeParameter.contextBoundTypeElement.zipWithIndex
      } yield (typeParameter, bound, idx)

      bounds.find {
        case (typeParameter, typeElement, index) => contextBoundParameterName(typeParameter, typeElement, index) == parameter.name
      }.fold(default)(function)
    }

  /** Creates a synthetic parameter clause based on view and context bounds */
  def syntheticParamClause(parameterOwner: ScTypeParametersOwner,
                           paramClauses: ScParameters,
                           isClassParameter: Boolean)
                          (hasImplicit: Boolean = paramClauses.clauses.exists(_.isImplicit)): Option[ScParameterClause] = {
    if (hasImplicit) return None

    val namedTypeParameters = parameterOwner.typeParameters.zipMapped(_.name)
    if (namedTypeParameters.isEmpty) return None

    case class ParameterDescriptor(typeParameter: ScTypeParam,
                                   name: String,
                                   typeElement: ScTypeElement,
                                   index: Int)

    val views = namedTypeParameters.flatMap {
      case (typeParameter, name) => typeParameter.viewTypeElement.map((typeParameter, name, _))
    }.zipWithIndex.map {
      case ((typeParameter, name, typeElement), index) => ParameterDescriptor(typeParameter, name, typeElement, index + 1)
    }

    val viewsTexts = views.map {
      case ParameterDescriptor(_, name, typeElement, index) =>
        val needParenthesis = typeElement match {
          case _: ScCompoundTypeElement |
               _: ScInfixTypeElement |
               _: ScFunctionalTypeElement |
               _: ScExistentialTypeElement => true
          case _ => false
        }
        import typeElement.projectContext
        s"ev$$$index: $name $functionArrow ${typeElement.getText.parenthesize(needParenthesis)}"
    }

    val bounds = for {
      (typeParameter, name) <- namedTypeParameters
      (typeElement, index) <- typeParameter.contextBoundTypeElement.zipWithIndex
    } yield ParameterDescriptor(typeParameter, name, typeElement, index)

    val boundsTexts = bounds.map {
      case ParameterDescriptor(typeParameter, name, typeElement, index) =>
        s"${contextBoundParameterName(typeParameter, typeElement, index)} : (${typeElement.getText})[$name]"
    }

    // if the context-applied compiler plugin is active, generate additional implicits params with default values
    // which are named by the type params with context bounds and combine all bounds as a compound type
    // to mimick context-applied's behavior
    val (contextAppliedVirtualBounds, contextAppliedVirtualBoundsTexts) =
      if (parameterOwner.contextAppliedEnabled &&
        // context-applied doesn't work inside classes extending AnyVal
        paramClauses.parentOfType[ScTypeDefinition].forall(_.superTypes.forall(_ != StdTypes.instance(parameterOwner.projectContext).AnyVal))) {
        val paramNames = paramClauses.getParameters.map(_.getName)
        val caBounds = for {
          (typeParameter, name) <- namedTypeParameters
          if typeParameter.contextBoundTypeElement.nonEmpty
          // if there's already a param with the name of type param, then skip this type param
          if !paramNames.contains(name)
          // reverse is necessary for bounds with common ancestors, resolving ancestor methods to the first bound
          boundTypes = typeParameter.contextBoundTypeElement.reverse
          compoundText = boundTypes.map(_.getText + s"[$name]").mkString(" with ")
          compound = createTypeElementFromText(compoundText)(typeParameter.projectContext)
        } yield ParameterDescriptor(typeParameter, name, compound, 0)

        val caBoundsTexts = caBounds.map {
          case ParameterDescriptor(_, name, typeElement, _) =>
            // default value avoids red squiggles at call site
            s"$name : ${typeElement.getText} = ???"
        }

        (caBounds, caBoundsTexts)
      } else (Nil, Nil)

    val clausesTexts = viewsTexts ++ boundsTexts ++ contextAppliedVirtualBoundsTexts
    if (clausesTexts.isEmpty) return None

    val result = createImplicitClauseFromTextWithContext(clausesTexts, paramClauses, isClassParameter)
    result.parameters
      .flatMap(_.typeElement)
      .zip(views ++ bounds ++ contextAppliedVirtualBounds)
      .foreach {
        case (typeElement, ParameterDescriptor(_, _, context, _)) => context.analog = typeElement
      }

    Some(result)
  }

  //todo: fix it
  // This is a conservative approximation, we should really resolve the operation
  // to differentiate self assignment from calling a method whose name happens to be an assignment operator.
  def isPossiblyAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignment if assign.leftExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1@ScReferenceExpression.withQualifier(`elem`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }

  def availableImportAliases(position: PsiElement): Set[(ScReference, String)] = {
    def getSelectors(holder: ScImportsHolder): Set[(ScReference, String)] = Option(holder).toSeq.flatMap {
      _.getImportStatements
    }.flatMap {
      _.importExprs
    }.flatMap {
      _.selectors
    }.flatMap {
      selector =>
        selector.reference.zip(selector.importedName).headOption
    }.filter {
      case (_, "_") => false
      case (reference, name) => reference.refName != name
    }.toSet

    if (position != null && !position.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      return Set.empty

    var parent = position.getParent
    val aliases = collection.mutable.Set[(ScReference, String)]()
    while (parent != null) {
      parent match {
        case holder: ScImportsHolder => aliases ++= getSelectors(holder)
        case _ =>
      }
      parent = parent.getParent
    }

    def correctResolve(alias: (ScReference, String)): Boolean = {
      val (aliasRef, text) = alias
      val ref = createReferenceFromText(text, position.getContext, position)
      aliasRef.multiResolveScala(false)
        .exists(rr => ref.isReferenceTo(rr.element))
    }

    aliases.filter(_._1.getTextRange.getEndOffset < position.getTextOffset).filter(correctResolve).toSet
  }

  def importAliasFor(element: PsiElement, refPosition: PsiElement): Option[ScReference] = {
    val importAliases = availableImportAliases(refPosition)
    val suitableAliases = importAliases.collect {
      case (aliasRef, aliasName)
        if aliasRef.multiResolveScala(false).exists(rr => ScEquivalenceUtil.smartEquivalence(rr.getElement, element)) => aliasName
    }
    if (suitableAliases.nonEmpty) {
      val newRef: ScStableCodeReference = createReferenceFromText(suitableAliases.head)(refPosition.getManager)
      Some(newRef)
    } else None
  }

  def isViableForAssignmentFunction(fun: ScFunction): Boolean = {
    val clauses = fun.paramClauses.clauses
    clauses.isEmpty || (clauses.length == 1 && clauses.head.isImplicit)
  }

  def padWithWhitespaces(element: PsiElement) {
    val range: TextRange = element.getTextRange
    val previousOffset = range.getStartOffset - 1
    val nextOffset = range.getEndOffset
    for {
      file <- element.containingFile
      prevElement = file.findElementAt(previousOffset)
      nextElement = file.findElementAt(nextOffset)
      parent <- element.parent
    } {
      if (!prevElement.isInstanceOf[PsiWhiteSpace]) {
        parent.addBefore(createWhitespace(element.getManager), element)
      }
      if (!nextElement.isInstanceOf[PsiWhiteSpace]) {
        parent.addAfter(createWhitespace(element.getManager), element)
      }
    }
  }

  def findInstanceBinding(instance: ScExpression): Option[ScBindingPattern] = {
    instance match {
      case _: ScNewTemplateDefinition =>
      case ref: ScReferenceExpression if ref.resolve().isInstanceOf[ScObject] =>
      case _ => return None
    }
    val nameContext = PsiTreeUtil.getParentOfType(instance, classOf[ScVariableDefinition], classOf[ScPatternDefinition])
    val (bindings, expr) = nameContext match {
      case vd: ScVariableDefinition => (vd.bindings, vd.expr)
      case td: ScPatternDefinition => (td.bindings, td.expr)
      case _ => (Seq.empty[ScBindingPattern], None)
    }
    if (bindings.size == 1 && expr.contains(instance)) Option(bindings.head)
    else {
      for (bind <- bindings) {
        if (bind.`type`().toOption == instance.`type`().toOption) return Option(bind)
      }
      None
    }
  }

  private def addBefore[T <: PsiElement](element: T, parent: PsiElement, anchorOpt: Option[PsiElement]): T = {
    val anchor = anchorOpt match {
      case Some(a) => a
      case None =>
        val last = parent.getLastChild
        if (isLineTerminator(last.getPrevSibling)) last.getPrevSibling
        else last
    }

    def addBefore(e: PsiElement) = parent.addBefore(e, anchor)

    def newLine: PsiElement = createNewLine()(element.getManager)

    val anchorEndsLine = isLineTerminator(anchor)
    if (anchorEndsLine) addBefore(newLine)

    val anchorStartsLine = isLineTerminator(anchor.getPrevSibling)
    if (!anchorStartsLine) addBefore(newLine)

    val addedStmt = addBefore(element).asInstanceOf[T]

    if (!anchorEndsLine) addBefore(newLine)
    else anchor.replace(newLine)

    addedStmt
  }

  def addStatementBefore(stmt: ScBlockStatement, parent: PsiElement, anchorOpt: Option[PsiElement]): ScBlockStatement = {
    addBefore[ScBlockStatement](stmt, parent, anchorOpt)
  }

  def addTypeAliasBefore(typeAlias: ScTypeAlias, parent: PsiElement, anchorOpt: Option[PsiElement]): ScTypeAlias = {
    addBefore[ScTypeAlias](typeAlias, parent, anchorOpt)
  }

  def isImplicit(namedElement: PsiNamedElement): Boolean = {
    namedElement match {
      case Implicit0Binding()                        => true /** See [[org.jetbrains.plugins.scala.util.BetterMonadicForSupport]] */
      case owner: ScModifierListOwner                => isImplicit(owner: ScModifierListOwner)
      case inNameContext(owner: ScModifierListOwner) => isImplicit(owner)
      case _                                         => false
    }
  }

  def isImplicit(modifierListOwner: ScModifierListOwner): Boolean = modifierListOwner match {
    case p: ScParameter => p.isImplicitParameter
    case _              => modifierListOwner.hasModifierProperty("implicit")
  }

  def replaceBracesWithParentheses(element: ScalaPsiElement): Unit = {
    import element.projectContext

    val block = createElementFromText("(_)")

    for (lBrace <- Option(element.findFirstChildByType(ScalaTokenTypes.tLBRACE))) {
      lBrace.replace(block.getFirstChild)
    }

    for (rBrace <- Option(element.findFirstChildByType(ScalaTokenTypes.tRBRACE))) {
      rBrace.replace(block.getLastChild)
    }
  }

  //reference in assignment is resolved to var, but actually there is a "_=" method which is applied
  //todo: resolve reference correctly instead of hacking annotator
  def isUnderscoreEq(assign: ScAssignment, actualType: ScType): Boolean = {
    assign.leftExpression match {
      case Resolved(pat: ScBindingPattern, _) =>
        pat.containingClass match {
          case td: ScTemplateDefinition =>
            val signaturesByName = TypeDefinitionMembers.getSignatures(td).forName(pat.name + "_=").iterator
            signaturesByName.exists { sig =>
              sig.paramClauseSizes === Array(1) &&
                actualType.conforms(sig.substitutedTypes.head.head.apply())
            }
          case _ => false
        }

      case _ => false
    }
  }
}
