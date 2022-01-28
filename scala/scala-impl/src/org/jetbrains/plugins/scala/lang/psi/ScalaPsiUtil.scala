package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.extapi.psi.{ASTDelegatePsiElement, StubBasedPsiElementBase}
import com.intellij.lang.ASTNode
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiModifier.STATIC
import com.intellij.psi._
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt, _}
import org.jetbrains.plugins.scala.externalLibraries.bm4.Implicit0Binding
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScGivenPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.{SAMUtil, ScEquivalenceUtil}

import java.{util => ju}
import scala.annotation.tailrec
import scala.collection.mutable
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

  /**
   * Checks if thisClass subsumes base, i.e if base is thisClass or a super class of it (with self type)
   */
  def thisSubsumes(thisClass: PsiClass, base: PsiClass): Boolean = {
    object TypeOfThis {
      def unapply(td: ScTemplateDefinition): Option[ScType] =
        td.selfType.map(_.glb(td.getTypeWithProjections().getOrAny))
    }

    def checkWithSelfType =
      thisClass match {
        case TypeOfThis(thisType) =>
          thisType.conforms(ScalaType.designator(base))
        case _ => false
      }

    thisClass == base ||
      isInheritorDeep(thisClass, base) ||
      checkWithSelfType
  }

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
        def getType(expression: Expression): ScType =
          expression
            .getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)
            .tr
            .getOrAny
            .widenIfLiteral

        TupleType(s.map(getType)) match {
          case t if t.isNothing => None
          case t                => Some(t)
        }
    }

    maybeType.map(t => Seq(Expression(t, firstLeaf(context))))
  }

  def processImportLastParent(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement,
                              lastParent: PsiElement, typeResult: => TypeResult): Boolean = {
    lastParent match {
      case _: ScImportOrExportStmt =>
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

  def undefineMethodTypeParams(fun: PsiMethod): ScSubstitutor = {
    val typeParameters = fun match {
      case fun: ScFunction => fun.typeParametersWithExtension
      case fun: PsiMethod  => fun.getTypeParameters.toSeq
    }

    ScSubstitutor.bind(typeParameters)(UndefinedType(_, level = 1))
  }

  @tailrec
  def isOnlyVisibleInLocalFile(elem: PsiElement): Boolean = {
    (elem, elem.getContext) match {
      case (_,             _: ScPackageLike | _: ScalaFile | _: ScEarlyDefinitions) =>
        false
      case (mem: ScMember, _) if mem.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis) =>
        true
      case (_: ScMember, body: ScExtensionBody) =>
        isOnlyVisibleInLocalFile(body.getParent)
      case (mem: ScMember, _) =>
        val cls = mem.containingClass
        (cls == null) || isOnlyVisibleInLocalFile(cls)
      case _ =>
        true
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
    if (packageFqn.isEmpty) None
    else {
      val lastDot: Int = packageFqn.lastIndexOf('.')
      val name =
        if (lastDot < 0) ""
        else packageFqn.substring(0, lastDot)
      Option(ScPackageImpl.findPackage(project, name))
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
      override def visitExpression(expr: ScExpression): Unit = {
        //Implicit parameters
        expr.findImplicitArguments match {
          case Some(results) => for (r <- results if r != null) res = res ++ r.importsUsed
          case _ =>
        }

        //implicit conversions
        def addConversions(fromUnderscore: Boolean): Unit = {
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
    val builder = Seq.newBuilder[PsiElement]
    var child = commonParent.getNode.getFirstChildNode
    var writeBuffer = false
    while (child != null) {
      if (child.getTextRange.getStartOffset == startOffset) {
        writeBuffer = true
      }
      if (writeBuffer) builder += child.getPsi
      if (child.getTextRange.getEndOffset >= endOffset) {
        writeBuffer = false
      }
      child = child.getTreeNext
    }
    builder.result()
  }


  /**
   * @return maximal element of specified type starting at startOffset exactly and ending not farther than endOffset
   *         May return several elements if they have exactly same range.
   */
  def elementsAtRange[T <: PsiElement : ClassTag](file: PsiFile, startOffset: Int, endOffset: Int): Seq[T] = {
    def fit(e: PsiElement): Boolean =
      e != null && e.startOffset == startOffset && e.endOffset <= endOffset

    val startElem = file.findElementAt(startOffset)
    val allInRange = startElem.withParentsInFile.takeWhile(fit).toList.filterByType[T]
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

  def stubOrPsiNextSibling(element: PsiElement): PsiElement = getStubOrPsiSibling(element, next = true)

  def stubOrPsiPrevSibling(element: PsiElement): PsiElement = getStubOrPsiSibling(element, next = false)

  private def getStubOrPsiSibling(element: PsiElement, next: Boolean): PsiElement = {
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
      case stubbed: StubBasedPsiElementBase[_] => stubbed.getStub.asInstanceOf[StubElement[_]]
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
    TermSignature(x.name, Seq.empty, ScSubstitutor.empty, x, None)

  def superValsSignatures(x: PsiNamedElement, withSelfType: Boolean = false): Seq[TermSignature] = {
    val empty = Seq.empty
    val typed: ScTypedDefinition = x match {
      case x: ScTypedDefinition => x
      case _ => return empty
    }
    val clazz: ScTemplateDefinition = typed.nameContext match {
      case e@(_: ScValue | _: ScVariable | _: ScObject) if e.getParent.is[ScTemplateBody] ||
        e.getParent.is[ScEarlyDefinitions] =>
        e.asInstanceOf[ScMember].containingClass
      case e: ScClassParameter if e.isClassMember => e.containingClass
      case _ => return empty
    }
    if (clazz == null) return empty
    val s = namedElementSig(x)
    val signatures =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(clazz)
      else              TypeDefinitionMembers.getSignatures(clazz)
    val sigs = signatures.forName(x.name)
    val builder = Seq.newBuilder[TermSignature]
    sigs.get(s) match {
      case Some(node) if !withSelfType || node.info.namedElement == x =>
        builder ++= node.supers.map(_.info)
      case Some(node) =>
        builder ++= node.supers.map(_.info).filter(_.namedElement != x)

        // (self type in method name doesn't mean "this class" but a base class of scala self types (https://docs.scala-lang.org/tour/self-types.html)
        // e.g. `A` inf this code `trait T { self: A => }`
        //builder += node.info
      case _ =>
        //this is possible case: private member of library source class.
        //Problem is that we are building signatures over decompiled class.
    }


    val beanMethods = getBeanMethods(typed)
    beanMethods.foreach { method =>
      val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)
      sigs.get(new PhysicalMethodSignature(method, ScSubstitutor.empty)) match {
        case Some(node) if !withSelfType || node.info.namedElement == method =>
          builder ++= node.supers.map(_.info)
        case Some(node) =>
          builder ++= node.supers.map(_.info).filter(_.namedElement != method)
          //builder += node.info
        case _ =>
      }
    }

    builder.result()
  }

  def superTypeMembers(element: PsiNamedElement,
                       withSelfType: Boolean = false): Seq[PsiNamedElement] =
    superTypeSignatures(element, withSelfType).map(_.namedElement)

  def superTypeSignatures(element: PsiNamedElement,
                          withSelfType: Boolean = false): Seq[TypeSignature] = {

    val clazz: ScTemplateDefinition = element.nameContext match {
      case e@(_: ScTypeAlias | _: ScTrait | _: ScClass) if e.getParent.is[ScTemplateBody] => e.asInstanceOf[ScMember].containingClass
      case _ => return Seq.empty
    }
    val types =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(clazz)
      else TypeDefinitionMembers.getTypes(clazz)

    types.forName(element.name).findNode(element) match {
      case Some(x) if !withSelfType || x.info.namedElement == element =>
        x.supers.map(_.info)
      case Some(x) =>
        x.supers.map(_.info).filter(_.namedElement != element) :+ x.info
      case _ =>
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

  def adjustTypes(element: PsiElement, addImports: Boolean = true, useTypeAliases: Boolean = true): Unit = {
    TypeAdjuster.adjustFor(Seq(element), addImports, useTypeAliases)
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => true
      case _ => false
    }
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalMethodSignature] = {
    val isObject = clazz.is[ScObject]
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

  def getCompanionModule(typeDefinition: ScTypeDefinition): Option[ScTypeDefinition] =
    typeDefinition match {
      case scObject: ScObject =>
        if (scObject.isSyntheticObject)
          scObject.syntheticNavigationElement
            .asOptionOf[ScTypeDefinition]
        else
          scObject.baseCompanion
      case _: ScTypeDefinition =>
        typeDefinition.baseCompanion
          .orElse(typeDefinition.fakeCompanionModule)
    }

  def getCompanionModule(`class`: PsiClass): Option[ScTypeDefinition] =
    `class` match {
      case typeDefinition: ScTypeDefinition => getCompanionModule(typeDefinition)
      case _ => None
    }

  def withCompanionModule(`class`: PsiClass): List[PsiClass] =
    `class` :: getCompanionModule(`class`).toList

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
    //NOTE: member can be a Kotlin class (note that in Kotlin nested classes are inner by default)
    case _ if m.getLanguage.isKindOf(ScalaLanguage.INSTANCE) => false
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
      override def putAll(parentClass: PsiClass, mappings: Array[PsiType]): PsiSubstitutor = PsiSubstitutor.EMPTY

      override def isValid: Boolean = true

      override def put(classParameter: PsiTypeParameter, mapping: PsiType): PsiSubstitutor = PsiSubstitutor.EMPTY

      override def getSubstitutionMap: ju.Map[PsiTypeParameter, PsiType] = new ju.HashMap[PsiTypeParameter, PsiType]()

      override def substitute(`type`: PsiType): PsiType = {
        substitutor(`type`.toScType()).toPsiType
      }

      override def substitute(typeParameter: PsiTypeParameter): PsiType = {
        substitutor(TypeParameterType(typeParameter)).toPsiType
      }

      override def putAll(another: PsiSubstitutor): PsiSubstitutor = PsiSubstitutor.EMPTY

      override def substituteWithBoundsPromotion(typeParameter: PsiTypeParameter): PsiType = substitute(typeParameter)

      override def ensureValid(): Unit = {}
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

          val funTypeToken = caseClause.findLastChildByType(TokenSet.create(tFUNTYPE, tFUNTYPE_ASCII, ScalaTokenType.ImplicitFunctionArrow))
          if (funTypeToken.exists(element.getTextOffset < _.getTextOffset)) false
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
  *   ScTypedExpr	                0                   0	                        0	                0             0	              0        |    1
  *   ScMatchStmt	                0                   0	                        0	                0             0	              0        |    1
	*		-----------------------------------------------------------------------------------------------------------------------------------
  *	  Other                       0                   0	                        0	                0             0	              0             0
  * */
  def needParentheses(from: ScExpression, expr: ScExpression): Boolean = {
    def infixInInfixParentheses(parent: ScInfixExpr, child: ScInfixExpr): Boolean = {

      import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._

      if (parent.left == from) {
        val lid = parent.operation.getText
        val rid = child.operation.getText
        if (priority(lid) < priority(rid)) true
        else if (priority(rid) < priority(lid)) false
        else if (operatorAssociativity(lid) != operatorAssociativity(rid)) true
        else if (operatorAssociativity(lid) == Associativity.Right) true
        else false
      }
      else {
        val lid = child.operation.getText
        val rid = parent.operation.getText
        if (priority(lid) < priority(rid)) false
        else if (priority(rid) < priority(lid)) true
        else if (operatorAssociativity(lid) != operatorAssociativity(rid)) true
        else if (operatorAssociativity(lid) == Associativity.Right) false
        else true
      }
    }

    def tupleInInfixNeedParentheses(parent: ScInfixExpr, from: ScExpression): Boolean = {
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
              case Some(newExpr: ScExpression) => !newExpr.textMatches(text)
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
        case _ if !parent.is[ScExpression] => false
        case _ if expr.textMatches("_") => false
        case (_: ScTuple | _: ScBlock | _: ScXmlExpr, _) => false
        case (infix: ScInfixExpr, _: ScTuple) => tupleInInfixNeedParentheses(infix, from)
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
    case scalaFile: ScalaFile => !scalaFile.isMultipleDeclarationsAllowed
    case _: ScBlock | _: ScTemplateBody | _: ScPackaging | _: ScParameters |
         _: ScTypeParamClause | _: ScCaseClause | _: ScFor | _: ScExistentialClause |
         _: ScEarlyDefinitions | _: ScRefinement => true
    case e: ScPatternDefinition if e.getContext.is[ScCaseClause] => true // {case a => val a = 1}
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
        .flatMap(_.expr.flatMap(_.asOptionOfUnsafe[PsiLiteral]))
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
        params.lift(n)
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

    if (e.is[ScParameter]) {
      return true
    }

    val parent = e.getParent

    if (parent.is[ScGenerator, ScForBinding, ScCaseClause]) {
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
    def unapply(expr: ScExpression): Option[PsiMethod] = {
      // ! this extra check by class is a performance optimisation SCL-16559
      // even though their usage may look redundant
      if (!isPossibleByClass(expr))
        None
      else if (expectedFunctionalTypeKind(expr).isEmpty)
        None
      else
        expr match {
          case ref: ScReferenceExpression if !ref.getParent.is[MethodInvocation] =>
            referencedMethod(ref, hasExplicitUnderscore = false)
          case gc: ScGenericCall if !gc.getParent.is[MethodInvocation] =>
            referencedMethod(gc, hasExplicitUnderscore = false)
          case us: ScUnderscoreSection =>
            us.bindingExpr.flatMap(referencedMethod(_, hasExplicitUnderscore = true))
          case ScMethodCall(
          invoked @ (_: ScReferenceExpression | _: ScGenericCall | _: ScMethodCall),
          args
          ) if args.nonEmpty && args.forall(isSimpleUnderscore) =>
            referencedMethod(invoked, hasExplicitUnderscore = false)
          case mc: ScMethodCall if !mc.getParent.is[ScMethodCall] =>
            referencedMethod(mc, hasExplicitUnderscore = false).filter {
              case f: ScFunction => f.paramClauses.clauses.size > numberOfArgumentClauses(mc)
              case _             => false
            }
          case _ => None
        }
    }


    /**
     * @return true if a method can be eta-expanded non-explicitly (without using of _, like `foo _`)
     *         in presence of expected type
     */
    private def canBeAutoEtaExpanded(m: PsiMethod): Boolean = {
      import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
      lazy val isScala211 = m.scalaLanguageLevelOrDefault == Scala_2_11

      val res = m match {
        case f: ScFunctionDefinition =>
          val clauses = f.paramClauses.clauses
          val hasSomeNonEmptyClause = clauses.exists { clause =>
            val isEmptyClause =
              if (isScala211) false
              else clause.parameters.isEmpty
            !clause.isImplicit && !isEmptyClause
          }
          hasSomeNonEmptyClause
        case _ =>
          // java method
          m.hasParameters || isScala211
      }
      // debug info
      //println(f"canBeEtaExpanded    (isScala211: $isScala211)    ${m.getName}%-16s    $res")
      res
    }

    private def referencedMethod(
      expr:               ScExpression,
      hasExplicitUnderscore: Boolean
    ): Option[PsiMethod] = {

      @tailrec
      def inner(expr: ScExpression, hasExplicitUnderscore: Boolean): Option[PsiMethod] =
        expr match {
          case ResolvesTo(m: PsiMethod) =>
            if (hasExplicitUnderscore || canBeAutoEtaExpanded(m))
              Some(m)
            else
              None
          case gc: ScGenericCall =>
            inner(gc.referencedExpr, hasExplicitUnderscore)
          case us: ScUnderscoreSection if us.bindingExpr.isDefined =>
            inner(us.bindingExpr.get, hasExplicitUnderscore)
          case m: ScMethodCall =>
            inner(m.deepestInvokedExpr, hasExplicitUnderscore = false)
          case _ =>
            None
        }

      val res = inner(expr, hasExplicitUnderscore)
      // debugging info
      //if (isPossibleByClass(expr)) {
      // val document = PsiDocumentManager.getInstance(expr.getProject).getDocument(expr.getContainingFile)
      //  val line     = if (document == null) -1 else document.getLineNumber(expr.startOffset) + 1
      //  println(f"line: ${line}%-3s  referencedMethod   ${res.isDefined}%-5s   explicit: ${hasExplicitUnderscore}%-5s  ${expr.getText}    from    ${expr.getClass.getSimpleName}")
      //}
      res
    }

    private def isSimpleUnderscore(expr: ScExpression) = expr match {
      case _: ScUnderscoreSection => expr.textMatches("_")
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

    private sealed trait ExpectedFunctionalTypeKind
    private object ExpectedTypeKind {
      case object Function extends ExpectedFunctionalTypeKind
      case class ClassWithSAM(functionalType: ScType) extends ExpectedFunctionalTypeKind
    }

    private def expectedFunctionalTypeKind(expr: ScExpression): Option[ExpectedFunctionalTypeKind] = {
      val expectedType = expr.expectedType(fromUnderscore = false)
      expectedType.flatMap(expectedFunctionalTypeKind(_, expr))
    }

    private def expectedFunctionalTypeKind(expectedType: ScType, expr: ScExpression): Option[ExpectedFunctionalTypeKind] =
      expectedType match {
        case FunctionType(_, _) =>
          Some(ExpectedTypeKind.Function)
        case expected if expr.isSAMEnabled =>
          val samType = SAMUtil.toSAMType(expected, expr)
          samType.map(ExpectedTypeKind.ClassWithSAM)
        case _ =>
          None
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

  def isExtensionMethodSignature(signature: Signature): Boolean =
    signature.isExtensionMethod

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

  /**
   * Example: lets consider T2 in this code {{{
   * def foo[T1, T2 : Show : Render](x: T2): String = ???
   * }}}
   * typeParam   ~ T2 <br>
   * contextType ~ Show OR Render <br>
   * boundIndex  ~ 0 OR 1 <br>
   */
  case class ContextBoundInfo(typeParam: ScTypeParam, contextType: ScTypeElement, boundIndex: Int)

  /**
   * @param parameter physical parameter OR
   *                  synthetic implicit parameter corresponding to some context bound
   * @return None if `parameter` is a normal physical parameter<br>
   *         Some(context bound info) if parameter represents some synthetic implicit parameter of some context bound
   */
  def findSyntheticContextBoundInfo(parameter: ScParameter): Option[ContextBoundInfo] =
    if (parameter.isPhysical) None
    else extractSyntheticContextBoundInfo(parameter)

  private def extractSyntheticContextBoundInfo(contextParameter: ScParameter): Option[ContextBoundInfo] = {
    val maybeOwner: Option[ScTypeParametersOwner] = contextParameter.owner match {
      case ScPrimaryConstructor.ofClass(cls) => Option(cls)
      case other: ScTypeParametersOwner      => Option(other)
      case _                                 => None
    }

    val contextBounds: Seq[ContextBoundInfo] = for {
      owner         <- maybeOwner.toSeq
      typeParameter <- owner.typeParameters
      (bound, idx)  <- typeParameter.contextBoundTypeElement.zipWithIndex
    } yield ContextBoundInfo(typeParameter, bound, idx)

    contextBounds.find { case ContextBoundInfo(typeParameter, typeElement, index) =>
      val currentParameterName = contextBoundParameterName(typeParameter, typeElement, index)
      contextParameter.name == currentParameterName
    }
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

    val clausesTexts = viewsTexts ++ boundsTexts
    if (clausesTexts.isEmpty) return None

    val result = createImplicitClauseFromTextWithContext(clausesTexts, paramClauses, isClassParameter)
    result.parameters
      .flatMap(_.typeElement)
      .zip(views ++ bounds)
      .foreach {
        case (typeElement, ParameterDescriptor(_, _, context, _)) => context.analog = typeElement
      }

    Some(result)
  }

  def looksLikeAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignment if assign.leftExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1@ScReferenceExpression.withQualifier(`elem`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }

  def isPossiblyAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignment if assign.leftExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignment => true
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
        selector.reference.zip(selector.importedName)
    }.filter {
      case (_, "_") => false
      case (reference, name) => reference.refName != name
    }.toSet

    if (position != null && !position.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      return Set.empty

    var parent = position.getParent
    val aliases = mutable.Set[(ScReference, String)]()
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

    aliases.iterator
      .filter(_._1.getTextRange.getEndOffset < position.getTextOffset)
      .filter(correctResolve)
      .toSet
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

  def padWithWhitespaces(element: PsiElement): Unit = {
    val range: TextRange = element.getTextRange
    val previousOffset = range.getStartOffset - 1
    val nextOffset = range.getEndOffset
    for {
      file <- element.containingFile
      prevElement = file.findElementAt(previousOffset)
      nextElement = file.findElementAt(nextOffset)
      parent <- element.parent
    } {
      if (!prevElement.is[PsiWhiteSpace]) {
        parent.addBefore(createWhitespace(element.getManager), element)
      }
      if (!nextElement.is[PsiWhiteSpace]) {
        parent.addAfter(createWhitespace(element.getManager), element)
      }
    }
  }

  def findInstanceBinding(instance: ScExpression): Option[ScBindingPattern] = {
    instance match {
      case _: ScNewTemplateDefinition =>
      case ref: ScReferenceExpression if ref.resolve().is[ScObject] =>
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

  def isImplicit(namedElement: PsiNamedElement): Boolean =
    namedElement match {
      case _: ScGiven                                           => true
      case _: ScGivenPattern                                    => true
      case Implicit0Binding()                                   => true /** See [[BetterMonadicForSupport]] */
      case owner: ScModifierListOwner                           => hasImplicitModifier(owner)
      case inNameContext(owner: ScModifierListOwner)            => hasImplicitModifier(owner)
      case _                                                    => false
    }

  def hasImplicitModifier(modifierListOwner: ScModifierListOwner): Boolean = modifierListOwner match {
    case p: ScParameter => p.isImplicitOrContextParameter
    case _              => modifierListOwner.hasModifierProperty("implicit")
  }

  def replaceBracesWithParentheses(element: ScalaPsiElement): Unit = {
    import element.projectContext

    val block = createElementFromText("(_)")

    for (lBrace <- element.findFirstChildByType(ScalaTokenTypes.tLBRACE)) {
      lBrace.replace(block.getFirstChild)
    }

    for (rBrace <- element.findFirstChildByType(ScalaTokenTypes.tRBRACE)) {
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

  def deleteElementInCommaSeparatedList(list: ASTDelegatePsiElement, element: ASTNode): Unit = {
    val (end, foundAfterComma) = {
      var last = element
      var cur = element.getTreeNext
      while (cur.isWhitespaceOrComment) {
        last = cur
        cur = cur.getTreeNext
      }
      if (cur.hasElementType(ScalaTokenTypes.tCOMMA))
        (cur, true)
      else (last, false)
    }

    val begin = {
      var last = element
      var cur = element.getTreePrev
      while (cur.isWhitespaceOrComment) {
        last = cur
        cur = cur.getTreePrev
      }
      if (!foundAfterComma && cur.hasElementType(ScalaTokenTypes.tCOMMA))
        cur
      else last
    }

    CodeEditUtil.removeChildren(list.getNode, begin, end)
  }

  def generateGivenOrExtensionName(tes: ScTypeElement*): String = {
    // todo: implement correct naming : https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html#anonymous-given-instances
    var text = tes.map(_.getText).mkString("_")
    text = text.replaceAll("=>", "_to_")
    text = text.replaceAll("[^a-zA-Z_0-9]+", "_")
    text = text.replaceAll("(^_+)|(_+$)", "")

    "given_" + text
  }

  def constructTypeForPsiClass(
    clazz:              PsiClass,
    withTypeParameters: Boolean = true
  )(typeArgFun:         (PsiTypeParameter, Int) => ScType
  ): ScType =
    clazz match {
      case PsiClassWrapper(definition) => constructTypeForPsiClass(definition)(typeArgFun)
      case _ =>
        val designator = clazz.containingClass match {
          case null => ScDesignatorType(clazz)
          case cClass =>
            val isStatic  = clazz.hasModifierProperty(STATIC)
            val projected = constructTypeForPsiClass(cClass, withTypeParameters = !isStatic)(typeArgFun)
            ScProjectionType(projected, clazz)
        }

        if (withTypeParameters && clazz.hasTypeParameters)
          ScParameterizedType(
            designator,
            clazz.getTypeParameters.toSeq.zipWithIndex.map(typeArgFun.tupled)
          )
        else designator
    }

  @Nullable
  def getParentOfTypeStoppingAtBlocks[T <: PsiElement](element: PsiElement, clazz: Class[T], strict: Boolean): T =
    PsiTreeUtil.getParentOfType(
      element, clazz, strict, /*stopAt=*/ classOf[ScBlock], classOf[ScTemplateBody]
    )

  @Nullable
  def getParentOfTypeInsideImport[T <: PsiElement](element: PsiElement, clazz: Class[T], strict: Boolean): T =
    ScalaPsiUtil.getParentOfTypeStoppingAtBlocks(element, clazz, strict)

  def parentOfTypeInsideImport[T <: PsiElement](element: PsiElement, clazz: Class[T], strict: Boolean): Option[T] =
    Option(getParentOfTypeInsideImport(element, clazz, strict))

  @Nullable
  def getParentImportExpression(element: PsiElement): ScImportExpr =
    getParentOfTypeInsideImport(element, classOf[ScImportExpr], strict = true)

  def parentImportExpression(element: PsiElement): Option[ScImportExpr] =
    Option(getParentImportExpression(element))

  @Nullable
  def getParentImportStatement(element: PsiElement): ScImportStmt =
    getParentOfTypeInsideImport(element, classOf[ScImportStmt], strict = true)

  def parentImportStatement(element: PsiElement): Option[ScImportStmt] =
    Option(getParentImportStatement(element))
}
