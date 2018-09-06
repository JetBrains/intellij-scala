package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, _}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits._
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set, mutable}

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

  def typeParamString(param: ScTypeParam): String = {
    var paramText = param.name
    if (param.typeParameters.nonEmpty) {
      paramText += param.typeParameters.map(typeParamString).mkString("[", ", ", "]")
    }
    param.lowerTypeElement foreach { tp =>
      paramText = paramText + " >: " + tp.getText
    }
    param.upperTypeElement foreach { tp =>
      paramText = paramText + " <: " + tp.getText
    }
    param.viewTypeElement foreach { tp =>
      paramText = paramText + " <% " + tp.getText
    }
    param.contextBoundTypeElement foreach { tp =>
      paramText = paramText + " : " + tp.getText
    }
    paramText
  }

  def debug(message: => String, logger: Logger) {
    if (logger.isDebugEnabled) {
      logger.debug(message)
    }
  }

  def functionArrow(implicit project: ProjectContext): String = {
    val useUnicode = project != null && ScalaCodeStyleSettings.getInstance(project).REPLACE_CASE_ARROW_WITH_UNICODE_CHAR
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

  def isBooleanBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    if (noResolve) {
      s.annotations.map {
        _.typeElement.getText
      }.exists { text =>
        Set("scala.reflect.BooleanBeanProperty", "reflect.BooleanBeanProperty",
          "BooleanBeanProperty", "scala.beans.BooleanBeanProperty", "beans.BooleanBeanProperty").
          contains(text.replace(" ", ""))
      }
    } else {
      s.hasAnnotation("scala.reflect.BooleanBeanProperty") ||
        s.hasAnnotation("scala.beans.BooleanBeanProperty")
    }
  }

  def isBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    if (noResolve) {
      s.annotations.map {
        _.typeElement.getText
      }.exists { text =>
        Set("scala.reflect.BeanProperty", "reflect.BeanProperty",
          "BeanProperty", "scala.beans.BeanProperty", "beans.BeanProperty").
          contains(text.replace(" ", ""))
      }
    } else {
      s.hasAnnotation("scala.reflect.BeanProperty") ||
        s.hasAnnotation("scala.beans.BeanProperty")
    }
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
          val (result, _) = expression.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)
          result.getOrAny
        }

        TupleType(s.map(getType)) match {
          case t if t.isNothing => None
          case t => Some(t)
        }
    }

    maybeType.map { t =>
      Seq(new Expression(t, firstLeaf(context)))
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
    val subst = state.get(ScSubstitutor.key).toOption.getOrElse(ScSubstitutor.empty)
    lastParent match {
      case _: ScImportStmt =>
        typeResult match {
          case Right(t) =>
            (processor, place) match {
              case (b: BaseProcessor, p: ScalaPsiElement) => b.processType(subst subst t, p, state)
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
  def inferMethodTypesArgs(fun: PsiMethod, classSubst: ScSubstitutor): ScSubstitutor = {
    implicit val ctx: ProjectContext = fun
    val typeParameters = fun match {
      case fun: ScFunction => fun.typeParameters
      case fun: PsiMethod => fun.getTypeParameters.toSeq
    }
    ScSubstitutor.bind(typeParameters.map(TypeParameter(_)))(UndefinedType(_, level = 1))
  }

  def findImplicitConversion(baseExpr: ScExpression, refName: String, ref: ScExpression, processor: BaseProcessor,
                             noImplicitsForArgs: Boolean, precalcType: Option[ScType] = None): Option[ImplicitResolveResult] = {
    implicit val ctx: ProjectContext = baseExpr

    val exprType: ScType = precalcType match {
      case None => ImplicitCollector.exprType(baseExpr, fromUnder = false) match {
        case None => return None
        case Some(x) if x.equiv(Nothing) => return None //do not proceed with nothing type, due to performance problems.
        case Some(x) => x
      }
      case Some(x) if x.equiv(Nothing) => return None
      case Some(x) => x
    }
    val args = processor match {
      case _ if !noImplicitsForArgs => Seq.empty
      case m: MethodResolveProcessor => m.argumentClauses.flatMap { expressions =>
        expressions.map {
          _.getTypeAfterImplicitConversion(checkImplicits = false, isShape = m.isShapeResolve, None)._1.getOrAny
        }
      }
      case _ => Seq.empty
    }

    def checkImplicits(noApplicability: Boolean = false, withoutImplicitsForArgs: Boolean = noImplicitsForArgs): Seq[ScalaResolveResult] = {
      val data = ExtensionConversionData(baseExpr, ref, refName, processor, noApplicability, withoutImplicitsForArgs)

      implicit val elementScope: ElementScope = baseExpr.elementScope
      new ImplicitCollector(
        baseExpr,
        FunctionType(Any, Seq(exprType)),
        FunctionType(exprType, args),
        coreElement = None,
        isImplicitConversion = true,
        extensionData = Some(data)).collect()
    }

    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    val foundImplicits = checkImplicits() match {
      case Seq() => checkImplicits(noApplicability = true)
      case seq@Seq(_) => seq
      case _ => checkImplicits(withoutImplicitsForArgs = true)
    }

    foundImplicits match {
      case Seq(resolveResult) =>
        ExtensionConversionHelper.specialExtractParameterType(resolveResult).map {
          case (tp, typeParams) =>
            RegularImplicitResolveResult(resolveResult, tp, unresolvedTypeParameters = typeParams) //todo: from companion parameter
        }
      case _ => None
    }
  }

  def isLocalOrPrivate(elem: PsiElement): Boolean = {
    elem.getContext match {
      case _: ScPackageLike | _: ScalaFile | _: ScEarlyDefinitions => false
      case _: ScTemplateBody =>
        elem match {
          case mem: ScMember if mem.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis) => true
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
        else if (b.lastExpr.isEmpty) (-1, expr)
        else isAnonymousExpression(b.lastExpr.get)
      case p: ScParenthesisedExpr => p.innerElement match {
        case Some(x) => isAnonymousExpression(x)
        case _ => (-1, expr)
      }
      case f: ScFunctionExpr => (f.parameters.length, expr)
      case _ => (-1, expr)
    }
  }

  def isAnonExpression(expr: ScExpression): Boolean = isAnonymousExpression(expr)._1 >= 0

  def getModule(element: PsiElement): Module = {
    val index: ProjectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    if (element.getContainingFile.getVirtualFile != null)
      index.getModuleForFile(element.getContainingFile.getVirtualFile)
    else null
  }

  def collectImplicitObjects(_tp: ScType)
                            (implicit elementScope: ElementScope): Seq[ScType] = {
    val ElementScope(project, scope) = elementScope

    val tp = _tp.removeAliasDefinitions()
    val implicitObjectsCache = ScalaPsiManager.instance(project).collectImplicitObjectsCache
    val cacheKey = (tp, scope)
    var cachedResult = implicitObjectsCache.get(cacheKey)
    if (cachedResult != null) return cachedResult

    val visited: mutable.HashSet[ScType] = new mutable.HashSet[ScType]()
    val parts: mutable.Queue[ScType] = new mutable.Queue[ScType]

    def collectPartsIter(iterable: TraversableOnce[ScType]): Unit = {
      val iterator = iterable.toIterator
      while(iterator.hasNext) {
        collectParts(iterator.next())
      }
    }

    def collectPartsTr(option: TypeResult): Unit = {
      option match {
        case Right(t) => collectParts(t)
        case _ =>
      }
    }

    def collectParts(tp: ScType) {
      ProgressManager.checkCanceled()
      if (visited.contains(tp)) return
      visited += tp
      tp.isAliasType match {
        case Some(AliasType(_, _, Right(t))) => collectParts(t)
        case _ =>
      }

      def collectSupers(clazz: PsiClass, subst: ScSubstitutor) {
        clazz match {
          case td: ScTemplateDefinition =>
            collectPartsIter(td.superTypes.map(subst.subst))
          case clazz: PsiClass =>
            collectPartsIter(clazz.getSuperTypes.map(t => subst.subst(t.toScType())))
        }
      }

      tp match {
        case ScDesignatorType(v: ScBindingPattern) => collectPartsTr(v.`type`())
        case ScDesignatorType(v: ScFieldId) => collectPartsTr(v.`type`())
        case ScDesignatorType(p: ScParameter) => collectPartsTr(p.`type`())
        case ScCompoundType(comps, _, _) => collectPartsIter(comps)
        case ParameterizedType(a: ScAbstractType, args) =>
          collectParts(a)
          collectPartsIter(args)
        case p@ParameterizedType(des, args) =>
          p.extractClassType match {
            case Some((clazz, subst)) =>
              parts += des
              collectParts(des)
              collectPartsIter(args)
              collectSupers(clazz, subst)
            case _ =>
              collectParts(des)
              collectPartsIter(args)
          }
        case j: JavaArrayType =>
          val parameterizedType = j.getParameterizedType
          collectParts(parameterizedType.getOrElse(return))
        case proj@ScProjectionType(projected, _) =>
          collectParts(projected)
          proj.actualElement match {
            case v: ScBindingPattern => collectPartsTr(v.`type`().map(proj.actualSubst.subst))
            case v: ScFieldId => collectPartsTr(v.`type`().map(proj.actualSubst.subst))
            case v: ScParameter => collectPartsTr(v.`type`().map(proj.actualSubst.subst))
            case _ =>
          }
          tp.extractClassType match {
            case Some((clazz, subst)) =>
              parts += tp
              collectSupers(clazz, subst)
            case _ =>
          }
        case ScAbstractType(_, _, upper) =>
          collectParts(upper)
        case ScExistentialType(quant, _) => collectParts(quant)
        case tpt: TypeParameterType => collectParts(tpt.upperType)
        case _ =>
          tp.extractClassType match {
            case Some((clazz, subst)) =>
              var packObjects = new ArrayBuffer[ScTypeDefinition]()

              @tailrec
              def packageObjectsInImplicitScope(packOpt: Option[ScPackageLike]): Unit = packOpt match {
                case Some(pack) =>
                  pack.findPackageObject(scope).foreach(packObjects += _)
                  packageObjectsInImplicitScope(pack.parentScalaPackage)
                case _ =>
              }

              packageObjectsInImplicitScope(Option(ScalaPsiUtil.contextOfType(clazz, strict = false, classOf[ScPackageLike])))
              parts += tp
              packObjects.foreach(p => parts += ScDesignatorType(p))

              collectSupers(clazz, subst)
            case _ =>
          }
      }
    }

    collectParts(tp)
    val res: mutable.HashMap[String, Seq[ScType]] = new mutable.HashMap

    def addResult(fqn: String, tp: ScType): Unit = {
      res.get(fqn) match {
        case Some(s) =>
          if (s.forall(!_.equiv(tp))) {
            res.remove(fqn)
            res += ((fqn, s :+ tp))
          }
        case None => res += ((fqn, Seq(tp)))
      }
    }

    while (parts.nonEmpty) {
      val part = parts.dequeue()
      //here we want to convert projection types to right projections
      val visited = new mutable.HashSet[PsiClass]()

      @tailrec
      def collectObjects(tp: ScType) {
        tp match {
          case _ if tp.isAny =>
          case tp: StdType if Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char").contains(tp.name) =>
            elementScope.getCachedObject("scala." + tp.name)
              .foreach { o =>
              addResult(o.qualifiedName, ScDesignatorType(o))
            }
          case ScDesignatorType(ta: ScTypeAliasDefinition) => collectObjects(ta.aliasedType.getOrAny)
          case ScProjectionType.withActual(actualElem: ScTypeAliasDefinition, actualSubst) =>
            collectObjects(actualSubst.subst(actualElem.aliasedType.getOrAny))
          case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
            val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
            collectObjects(genericSubst.subst(ta.aliasedType.getOrAny))
          case ParameterizedType(ScProjectionType.withActual(actualElem: ScTypeAliasDefinition, actualSubst), args) =>
            val genericSubst = ScSubstitutor.bind(actualElem.typeParameters, args)
            val s = actualSubst.followed(genericSubst)
            collectObjects(s.subst(actualElem.aliasedType.getOrAny))
          case _ =>
            tp.extractClass match {
              case Some(obj: ScObject) if !visited.contains(obj) => addResult(obj.qualifiedName, tp)
              case Some(clazz) if !visited.contains(clazz) =>
                getCompanionModule(clazz) match {
                  case Some(obj: ScObject) =>
                    tp match {
                      case ScProjectionType(proj, _) =>
                        addResult(obj.qualifiedName, ScProjectionType(proj, obj))
                      case ParameterizedType(ScProjectionType(proj, _), _) =>
                        addResult(obj.qualifiedName, ScProjectionType(proj, obj))
                      case _ =>
                        addResult(obj.qualifiedName, ScDesignatorType(obj))
                    }
                  case _ =>
                }
              case _ =>
            }
        }
      }

      collectObjects(part)
    }
    cachedResult = res.values.flatten.toSeq
    implicitObjectsCache.put(cacheKey, cachedResult)
    cachedResult
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

  def mapToLazyTypesSeq(elems: Seq[PsiParameter]): Seq[() => ScType] = {
    elems.map(param => () =>
      param match {
        case scp: ScParameter => scp.`type`().getOrNothing
        case p: PsiParameter =>
          val treatJavaObjectAsAny = p.parentsInFile.findByType[PsiClass] match {
            case Some(cls) if cls.qualifiedName == "java.lang.Object" => true // See SCL-3036
            case _ => false
          }
          p.paramType(treatJavaObjectAsAny = treatJavaObjectAsAny)
      }
    )
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
          case f: ScForStatement =>
            f.getDesugarizedExpr match {
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
    val buffer = new ArrayBuffer[PsiElement]
    var child = commonParent.getNode.getFirstChildNode
    var writeBuffer = false
    while (child != null) {
      if (child.getTextRange.getStartOffset == startOffset) {
        writeBuffer = true
      }
      if (writeBuffer) buffer.append(child.getPsi)
      if (child.getTextRange.getEndOffset >= endOffset) {
        writeBuffer = false
      }
      child = child.getTreeNext
    }
    buffer
  }

  def isInvalidContextOrder(before: PsiElement, after: PsiElement, topLevel: Option[PsiElement]): Boolean = {
    if (before == after) return true

    val contexts = getContexts(before, topLevel.orNull) zip getContexts(after, topLevel.orNull)
    val firstDifference = contexts.find { case (a, b) => a != b }

    firstDifference.exists {
      case (beforeAncestor, afterAncestor) =>
        val beforeInContext = beforeAncestor.sameElementInContext
        val afterInContext = afterAncestor.sameElementInContext

        beforeInContext.withNextSiblings.contains(afterInContext)
    }
  }

  @tailrec
  def getContexts(elem: PsiElement, stopAt: PsiElement, acc: List[PsiElement] = Nil): List[PsiElement] = {
    elem.getContext match {
      case null | `stopAt` => acc
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

  def getNextStubOrPsiElement(elem: PsiElement): PsiElement =
    elem.stub match {
      case Some(stub) => stubOrPsiSibling(stub, +1, elem.getNextSibling)
      case None => elem.getNextSibling
    }

  def getPrevStubOrPsiElement(elem: PsiElement): PsiElement =
    elem.stub match {
      case Some(stub) => stubOrPsiSibling(stub, -1, elem.getPrevSibling)
      case None => elem.getPrevSibling
    }

  private def stubOrPsiSibling(stub: StubElement[_], delta: Int, byPsi: => PsiElement): PsiElement = {
    stub.getParentStub match {
      case null => byPsi
      case parent =>
        val children = parent.getChildrenStubs
        val index = children.indexOf(stub)
        val newIndex = index + delta

        if (index < 0)
          byPsi
        else if (newIndex < 0 || newIndex >= children.size)
          null
        else
          children.get(newIndex).getPsi
    }
  }

  def isLValue(elem: PsiElement): Boolean = elem match {
    case e: ScExpression => e.getParent match {
      case as: ScAssignStmt => as.getLExpression eq e
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

  def namedElementSig(x: PsiNamedElement): Signature =
    Signature(x.name, Seq.empty, ScSubstitutor.empty, x)

  def superValsSignatures(x: PsiNamedElement, withSelfType: Boolean = false): Seq[Signature] = {
    val empty = Seq.empty
    val typed: ScTypedDefinition = x match {
      case x: ScTypedDefinition => x
      case _ => return empty
    }
    val clazz: ScTemplateDefinition = typed.nameContext match {
      case e@(_: ScValue | _: ScVariable | _: ScObject) if e.getParent.isInstanceOf[ScTemplateBody] ||
        e.getParent.isInstanceOf[ScEarlyDefinitions] =>
        e.asInstanceOf[ScMember].containingClass
      case e: ScClassParameter if e.isEffectiveVal => e.containingClass
      case _ => return empty
    }
    if (clazz == null) return empty
    val s = namedElementSig(x)
    val signatures =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(clazz)
      else TypeDefinitionMembers.getSignatures(clazz)
    val sigs = signatures.forName(x.name)._1
    var res: Seq[Signature] = (sigs.get(s): @unchecked) match {
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


    val beanMethods = typed.getBeanMethods
    beanMethods.foreach {
      method =>
        val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)._1
        (sigs.get(new PhysicalSignature(method, ScSubstitutor.empty)): @unchecked) match {
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

    superTypeMembersAndSubstitutors(element, withSelfType).map(_.info)
  }

  def superTypeMembersAndSubstitutors(element: PsiNamedElement,
                                      withSelfType: Boolean = false): Seq[TypeDefinitionMembers.TypeNodes.Node] = {

    val clazz: ScTemplateDefinition = element.nameContext match {
      case e@(_: ScTypeAlias | _: ScTrait | _: ScClass) if e.getParent.isInstanceOf[ScTemplateBody] => e.asInstanceOf[ScMember].containingClass
      case _ => return Seq.empty
    }
    if (clazz == null) return Seq.empty
    val types = if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(clazz) else TypeDefinitionMembers.getTypes(clazz)
    val sigs = types.forName(element.name)._1
    (sigs.get(element): @unchecked) match {
      //partial match
      case Some(x) if !withSelfType || x.info == element => x.supers
      case Some(x) =>
        x.supers.filter {
          _.info != element
        } :+ x
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
    new LightModifierList(manager, ScalaLanguage.INSTANCE)

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

  def allMethods(clazz: PsiClass): Iterable[PhysicalSignature] =
    TypeDefinitionMembers.getSignatures(clazz).allFirstSeq().flatMap(_.filter {
      case (_, n) => n.info.isInstanceOf[PhysicalSignature]
    }).
      map {
        case (_, n) => n.info.asInstanceOf[PhysicalSignature]
      }

  def getMethodsForName(clazz: PsiClass, name: String): Seq[PhysicalSignature] = {
    for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(clazz).forName(name)._1
         if clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static")) yield n
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "apply")
  }

  def getUnapplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "unapply") ++ getMethodsForName(clazz, "unapplySeq") ++
      (clazz match {
        case c: ScObject => c.allSynthetics.filter(s => s.name == "unapply" || s.name == "unapplySeq").
          map(new PhysicalSignature(_, ScSubstitutor.empty))
        case _ => Seq.empty[PhysicalSignature]
      })
  }

  def getUpdateMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "update")
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
      case definition: ScTypeDefinition =>
        definition.baseCompanionModule.orElse(definition.fakeCompanionModule)
      case _ => None
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
    @tailrec
    def hasStablePathInner(m: PsiMember): Boolean = {
      m.getContext match {
        case _: PsiFile => return true
        case _: ScPackaging | _: PsiPackage => return true
        case _ =>
      }
      m.containingClass match {
        case null => false
        case o: ScObject if o.isPackageObject || o.qualifiedName == "scala.Predef" => true
        case o: ScObject => hasStablePathInner(o)
        case j if isStaticJava(m) => hasStablePathInner(j)
        case _ => false
      }
    }

    o.nameContext match {
      case member: PsiMember => hasStablePathInner(member)
      case _: ScPackaging | _: PsiPackage => true
      case _ => false
    }
  }

  def getPsiSubstitutor(subst: ScSubstitutor)
                       (implicit elementScope: ElementScope): PsiSubstitutor = {

    case class PseudoPsiSubstitutor(substitutor: ScSubstitutor) extends PsiSubstitutor {
      def putAll(parentClass: PsiClass, mappings: Array[PsiType]): PsiSubstitutor = PsiSubstitutor.EMPTY

      def isValid: Boolean = true

      def put(classParameter: PsiTypeParameter, mapping: PsiType): PsiSubstitutor = PsiSubstitutor.EMPTY

      def getSubstitutionMap: java.util.Map[PsiTypeParameter, PsiType] = new java.util.HashMap[PsiTypeParameter, PsiType]()

      def substitute(`type`: PsiType): PsiType = {
        substitutor.subst(`type`.toScType()).toPsiType
      }

      def substitute(typeParameter: PsiTypeParameter): PsiType = {
        substitutor.subst(TypeParameterType(typeParameter)).toPsiType
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
        case _: ScMatchStmt | _: ScalaFile | null => true
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
        case (_: ScGuard, _: ScMatchStmt) => true
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
        case (_: ScTypedStmt | _: ScMatchStmt, _) => true
        case _ => false
      }
    }
  }

  def isScope(element: PsiElement): Boolean = element match {
    case _: ScalaFile | _: ScBlock | _: ScTemplateBody | _: ScPackaging | _: ScParameters |
         _: ScTypeParamClause | _: ScCaseClause | _: ScForStatement | _: ScExistentialClause |
         _: ScEarlyDefinitions | _: ScRefinement => true
    case e: ScPatternDefinition if e.getContext.isInstanceOf[ScCaseClause] => true // {case a => val a = 1}
    case _ => false
  }

  def stringValueOf(e: PsiLiteral): Option[String] = e.getValue.toOption.flatMap(_.asOptionOf[String])

  def readAttribute(annotation: PsiAnnotation, name: String): Option[String] = {
    annotation.findAttributeValue(name) match {
      case literal: PsiLiteral => stringValueOf(literal)
      case element: ScReferenceElement => element.getReference.toOption
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
      case ScAssignStmt(ResolvesTo(p: ScParameter), Some(_)) => Some(Parameter(p))
      case _ =>
        exp.getParent match {
          case parenth: ScParenthesisedExpr => parameterOf(parenth)
          case named: ScAssignStmt => parameterOf(named)
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
    param.parentOfType(classOf[ScFunction]).flatMap {
      case fun if fun.isSyntheticCopy => Option(fun.containingClass)
      case fun if fun.isSyntheticApply => getCompanionModule(fun.containingClass)
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
      parent.isInstanceOf[ScEnumerator] ||
      parent.isInstanceOf[ScCaseClause]) {
      return true
    }

    e.parentsInFile.takeWhile(!isScope(_)).containsType[ScPatternDefinition]
  }

  private def isCanonicalArg(expr: ScExpression) = expr match {
    case _: ScParenthesisedExpr => false
    case ScBlock(_: ScExpression) => false
    case _ => true
  }

  def isByNameArgument(expr: ScExpression): Boolean = {
    isCanonicalArg(expr) && ScalaPsiUtil.parameterOf(expr).exists(_.isByName)
  }

  def isArgumentOfFunctionType(expr: ScExpression): Boolean = {

    isCanonicalArg(expr) && parameterOf(expr).exists(p => FunctionType.isFunctionType(p.paramType))
  }

  object MethodValue {
    def unapply(expr: ScExpression): Option[PsiMethod] = {
      if (!expr.expectedType(fromUnderscore = false).exists {
        case FunctionType(_, _) => true
        case expected if expr.isSAMEnabled =>
          toSAMType(expected, expr).isDefined
        case _ => false
      }) {
        return None
      }
      expr match {
        case ref: ScReferenceExpression if !ref.getParent.isInstanceOf[MethodInvocation] => referencedMethod(ref, canBeParameterless = false)
        case gc: ScGenericCall if !gc.getParent.isInstanceOf[MethodInvocation] => referencedMethod(gc, canBeParameterless = false)
        case us: ScUnderscoreSection => us.bindingExpr.flatMap(referencedMethod(_, canBeParameterless = true))
        case ScMethodCall(invoked@(_: ScReferenceExpression | _: ScGenericCall | _: ScMethodCall), args)
          if args.nonEmpty && args.forall(isSimpleUnderscore) => referencedMethod(invoked, canBeParameterless = false)
        case mc: ScMethodCall if !mc.getParent.isInstanceOf[ScMethodCall] =>
          referencedMethod(mc, canBeParameterless = false).filter {
            case f: ScFunction if f.paramClauses.clauses.size > numberOfArgumentClauses(mc) => true
            case _ => false
          }
        case _ => None
      }
    }

    @tailrec
    private def referencedMethod(expr: ScExpression, canBeParameterless: Boolean): Option[PsiMethod] = {
      expr match {
        case ResolvesTo(f: ScFunctionDefinition) if f.isParameterless && !canBeParameterless => None
        case ResolvesTo(m: PsiMethod) => Some(m)
        case gc: ScGenericCall => referencedMethod(gc.referencedExpr, canBeParameterless)
        case us: ScUnderscoreSection if us.bindingExpr.isDefined => referencedMethod(us.bindingExpr.get, canBeParameterless)
        case m: ScMethodCall => referencedMethod(m.deepestInvokedExpr, canBeParameterless = false)
        case _ => None
      }
    }

    private def isSimpleUnderscore(expr: ScExpression) = expr match {
      case _: ScUnderscoreSection => expr.getText == "_"
      case typed: ScTypedStmt => Option(typed.expr).map(_.getText).contains("_")
      case _ => false
    }

    private def numberOfArgumentClauses(mc: ScMethodCall): Int = {
      mc.getEffectiveInvokedExpr match {
        case m: ScMethodCall => 1 + numberOfArgumentClauses(m)
        case _ => 1
      }
    }
  }

  private def contextBoundParameterName(typeParameter: ScTypeParam, bound: ScTypeElement): String = {
    val boundName = bound match {
      case ScSimpleTypeElement(Some(ref)) => ref.refName
      case projection: ScTypeProjection   => projection.refName
      case _                              => bound.getText
    }
    val tpName = typeParameter.name
    StringUtil.decapitalize(s"$boundName$$$tpName")
  }

  def originalContextBound(parameter: ScParameter): Option[(ScTypeParam, ScTypeElement)] = {
    if (parameter.isPhysical) return None

    val ownerTypeParams = parameter.owner.asOptionOf[ScTypeParametersOwner].toSeq.flatMap(_.typeParameters)
    val bounds = ownerTypeParams.flatMap(tp => tp.contextBoundTypeElement.map((tp, _)))
    bounds.find {
      case (tp, te) => contextBoundParameterName(tp, te) == parameter.name
    }
  }

  /** Creates a synthetic parameter clause based on view and context bounds */
  def syntheticParamClause(parameterOwner: ScTypeParametersOwner,
                           paramClauses: ScParameters,
                           isClassParameter: Boolean)
                          (hasImplicit: Boolean = paramClauses.clauses.exists(_.isImplicit)): Option[ScParameterClause] = {
    if (hasImplicit) return None

    val typeParameters = parameterOwner.typeParameters

    val views = typeParameters.flatMap { typeParameter =>
      val parameterName = typeParameter.name
      typeParameter.viewTypeElement.map { typeElement =>
        val needParenthesis = typeElement match {
          case _: ScCompoundTypeElement |
               _: ScInfixTypeElement |
               _: ScFunctionalTypeElement |
               _: ScExistentialTypeElement => true
          case _ => false
        }

        val elementText = typeElement.getText.parenthesize(needParenthesis)
        import typeElement.projectContext
        s"$parameterName $functionArrow $elementText"
      }
    }

    val bounds = typeParameters.flatMap { typeParameter =>
      val parameterName = typeParameter.name
      typeParameter.contextBoundTypeElement.map { typeElement =>
        val syntheticName = contextBoundParameterName(typeParameter, typeElement)
        s"$syntheticName : ${typeElement.getText}[$parameterName]"
      }
    }

    val clauses = views.zipWithIndex.map {
      case (text, index) => s"ev$$${index + 1}: $text"
    } ++ bounds

    val result = createImplicitClauseFromTextWithContext(clauses, paramClauses, isClassParameter)
    result.toSeq
      .flatMap(_.parameters)
      .flatMap(_.typeElement)
      .zip(typeParameters.flatMap(_.viewTypeElement) ++ typeParameters.flatMap(_.contextBoundTypeElement))
      .foreach {
        case (typeElement, context) => context.analog = typeElement
      }

    result
  }

  //todo: fix it
  // This is a conservative approximation, we should really resolve the operation
  // to differentiate self assignment from calling a method whose name happens to be an assignment operator.
  def isPossiblyAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignStmt if assign.getLExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1@ScReferenceExpression.withQualifier(`elem`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }

  def availableImportAliases(position: PsiElement): Set[(ScReferenceElement, String)] = {
    def getSelectors(holder: ScImportsHolder): Set[(ScReferenceElement, String)] = Option(holder).toSeq.flatMap {
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
    val aliases = collection.mutable.Set[(ScReferenceElement, String)]()
    while (parent != null) {
      parent match {
        case holder: ScImportsHolder => aliases ++= getSelectors(holder)
        case _ =>
      }
      parent = parent.getParent
    }

    def correctResolve(alias: (ScReferenceElement, String)): Boolean = {
      val (aliasRef, text) = alias
      val ref = createReferenceFromText(text, position.getContext, position)
      aliasRef.multiResolveScala(false)
        .exists(rr => ref.isReferenceTo(rr.element))
    }

    aliases.filter(_._1.getTextRange.getEndOffset < position.getTextOffset).filter(correctResolve).toSet
  }

  def importAliasFor(element: PsiElement, refPosition: PsiElement): Option[ScReferenceElement] = {
    val importAliases = availableImportAliases(refPosition)
    val suitableAliases = importAliases.collect {
      case (aliasRef, aliasName)
        if aliasRef.multiResolveScala(false).exists(rr => ScEquivalenceUtil.smartEquivalence(rr.getElement, element)) => aliasName
    }
    if (suitableAliases.nonEmpty) {
      val newRef: ScStableCodeReferenceElement = createReferenceFromText(suitableAliases.head)(refPosition.getManager)
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

  def intersectScopes(scope: SearchScope, scopeOption: Option[SearchScope]): SearchScope = {
    scopeOption match {
      case Some(s) => s.intersectWith(scope)
      case None => scope
    }
  }

  private def addBefore[T <: PsiElement](element: T, parent: PsiElement, anchorOpt: Option[PsiElement]): T = {
    val anchor = anchorOpt match {
      case Some(a) => a
      case None =>
        val last = parent.getLastChild
        if (ScalaPsiUtil.isLineTerminator(last.getPrevSibling)) last.getPrevSibling
        else last
    }

    def addBefore(e: PsiElement) = parent.addBefore(e, anchor)

    def newLine: PsiElement = createNewLine()(element.getManager)

    val anchorEndsLine = ScalaPsiUtil.isLineTerminator(anchor)
    if (anchorEndsLine) addBefore(newLine)

    val anchorStartsLine = ScalaPsiUtil.isLineTerminator(anchor.getPrevSibling)
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

  def changeVisibility(member: ScModifierListOwner, newVisibility: String): Unit = {
    implicit val projectContext: ProjectContext = member.projectContext
    val modifierList = member.getModifierList
    if (newVisibility == "" || newVisibility == "public") {
      modifierList.accessModifier.foreach(_.delete())
      return
    }
    val newElem = createModifierFromText(newVisibility)
    modifierList.accessModifier match {
      case Some(mod) => mod.replace(newElem)
      case None =>
        if (modifierList.children.isEmpty) {
          modifierList.add(newElem)
        } else {
          val mod = modifierList.getFirstChild
          modifierList.addBefore(newElem, mod)
          modifierList.addBefore(createWhitespace, mod)
        }
    }
  }

  /**
    * Determines if expected can be created with a Single Abstract Method and if so return the required ScType for it
    *
    * @see SCL-6140
    * @see https://github.com/scala/scala/pull/3018/
    */
  def toSAMType(expected: ScType, element: PsiElement): Option[ScType] = {
    implicit val scalaScope: ElementScope = ElementScope(element)
    val languageLevel = element.scalaLanguageLevelOrDefault

    def constructorValidForSAM(constructor: PsiMethod): Boolean = {
      val isPublicAndParameterless = constructor.getModifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
        constructor.getParameterList.getParametersCount == 0
      constructor match {
        case scalaConstr: ScPrimaryConstructor if isPublicAndParameterless => scalaConstr.effectiveParameterClauses.size < 2
        case _ => isPublicAndParameterless
      }
    }

    def isSAMable(td: ScTemplateDefinition): Boolean = {
      def selfTypeValid: Boolean = {
        td.selfType match {
          case Some(selfParam: ScParameterizedType) => td.`type`() match {
            case Right(classParamTp: ScParameterizedType) => selfParam.designator.conforms(classParamTp.designator)
            case _ => false
          }
          case Some(selfTp) => td.`type`() match {
            case Right(classType) => selfTp.conforms(classType)
            case _ => false
          }
          case _ => true
        }
      }
      def selfTypeCorrectIfScala212 = languageLevel == ScalaLanguageLevel.Scala_2_11 || selfTypeValid

      def validConstructor: Boolean = {
        td match {
          case cla: ScClass => cla.constructor.fold(false)(constructorValidForSAM)
          case _: ScTrait => true
          case _ => false
        }
      }

      if (td.isEffectivelyFinal || td.hasModifierPropertyScala("sealed")) false
      else validConstructor && selfTypeCorrectIfScala212
    }

    expected.extractClassType.flatMap {
      case (templDef: ScTemplateDefinition, typeSubst) =>
        if (!isSAMable(templDef)) None
        else {
          val abstractMembers = templDef.allSignatures.filter(TypeDefinitionMembers.ParameterlessNodes.isAbstract)
          val singleAbstractMethod = abstractMembers match {
            case Seq(PhysicalSignature(fun: ScFunction, subst)) => Some((fun, subst))
            case _ => None
          }

          for {
            (fun, methodSubst) <- singleAbstractMethod
            if fun.paramClauses.clauses.length == 1 && !fun.hasTypeParameters
            tp <- fun.`type`().toOption
          } yield {
            val substituted = methodSubst.followed(typeSubst).subst(tp)
            extrapolateWildcardBounds(substituted, expected, languageLevel).getOrElse {
              substituted
            }
          }
        }
      case (cl, substitutor) =>
        def isAbstract(m: PsiMethod): Boolean = m.hasAbstractModifier && m.findSuperMethods().forall(_.hasAbstractModifier)

        val visibleMethods = cl.getVisibleSignatures.asScala.map(_.getMethod).toList
        val abstractMethods = visibleMethods.filter(isAbstract)

        // must have exactly one abstract member and SAM must be monomorphic
        val singleAbstract =
          if (abstractMethods.length == 1) abstractMethods.headOption.filter(!_.hasTypeParameters)
          else None

        val validConstructorExists = cl.getConstructors match {
          case Array() => true
          case constructors => constructors.exists(constructorValidForSAM)
        }

        if (!validConstructorExists) None
        else singleAbstract.map { method =>
          val methodWithSubst =
            cl.findMethodsAndTheirSubstitutorsByName(method.getName, /*checkBases*/ true)
              .asScala
              .find(pair => isAbstract(pair.first))

          val methodSubst = methodWithSubst match {
            case Some(p: Pair[_, _]) => p.second
            case _ => PsiSubstitutor.EMPTY
          }
          val returnType = methodSubst.substitute(method.getReturnType)
          val parametersTypes = method.parameters.map { p =>
            methodSubst.substitute(p.getTypeElement.getType)
          }

          val functionType = FunctionType(returnType.toScType(), parametersTypes.map(_.toScType()))
          val substituted = substitutor.subst(functionType)

          extrapolateWildcardBounds(substituted, expected, languageLevel).getOrElse {
            substituted
          }
        }
    }
  }

  /**
    * In some cases existential bounds can be simplified without losing precision
    *
    * trait Comparinator[T] { def compare(a: T, b: T): Int }
    *
    * trait Test {
    * def foo(a: Comparinator[_ >: String]): Int
    * }
    *
    * can be simplified to:
    *
    * trait Test {
    * def foo(a: Comparinator[String]): Int
    * }
    *
    * @see https://github.com/scala/scala/pull/4101
    * @see SCL-8956
    */
  private def extrapolateWildcardBounds(tp: ScType, expected: ScType, scalaVersion: ScalaLanguageLevel)
                                       (implicit elementScope: ElementScope): Option[ScType] = {
    expected match {
      case ScExistentialType(ParameterizedType(_, _), wildcards) =>
        tp match {
          case FunctionType(retTp, params) =>
            def convertParameter(tpArg: ScType, variance: Variance): ScType = {
              tpArg match {
                case ParameterizedType(des, tpArgs) => ScParameterizedType(des, tpArgs.map(convertParameter(_, variance)))
                case ScExistentialType(parameterized: ScParameterizedType, _) if scalaVersion == ScalaLanguageLevel.Scala_2_11 =>
                  ScExistentialType(convertParameter(parameterized, variance)).simplify()
                case arg: ScExistentialArgument if wildcards.contains(arg) =>
                  (arg.lower, arg.upper) match {
                    // todo: Produces Bad code is green
                    // Problem is in Java wildcards. How to convert them if it's _ >: Lower, when generic has Upper.
                    // Earlier we converted with Any upper type, but then it was changed because of type incompatibility.
                    // Right now the simplest way is Bad Code is Green as otherwise we need to fix this inconsistency somehow.
                    // I has no idea how yet...
                    case (lo, _) if variance == Contravariant => lo
                    case (lo, hi) if lo.isNothing && variance == Covariant => hi
                    case _ => tpArg
                  }
                case arg: ScExistentialArgument =>
                  arg.copyWithBounds(convertParameter(arg.lower, variance), convertParameter(arg.upper, variance))
                case _ => tpArg
              }
            }

            //parameter clauses are contravariant positions, return types are covariant positions
            val newParams = params.map(convertParameter(_, Contravariant))
            val newRetTp = convertParameter(retTp, Covariant)
            Some(FunctionType(newRetTp, newParams))
          case _ => None
        }
      case _ => None
    }
  }

  def isImplicit(namedElement: PsiNamedElement): Boolean = {
    val maybeModifierListOwner = namedElement match {
      case owner: ScModifierListOwner => Some(owner)
      case named: ScNamedElement =>
        Option(named.nameContext).collect {
          case owner: ScModifierListOwner => owner
        }
      case _ => None
    }

    maybeModifierListOwner
      .exists(isImplicit)
  }

  def isImplicit(modifierListOwner: ScModifierListOwner): Boolean = modifierListOwner match {
    case p: ScParameter => p.isImplicitParameter
    case _ => modifierListOwner.hasModifierProperty("implicit")
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

  def isUnderscoreEq(assign: ScAssignStmt, actualType: ScType): Boolean = {
    assign.getLExpression match {
      case ref: ScReferenceExpression =>
        ref.bind().map(_.element).exists {
          case pat: ScBindingPattern =>
            Option(pat.containingClass).exists(_.allSignatures.find(_.name == pat.name + "_=").exists {
              sig =>
                sig.paramLength.length == 1 && sig.paramLength.head == 1 &&
                  actualType.conforms(sig.substitutedTypes.head.head.apply())
            })
          case _ => false
        }
      case _ => false
    }
  }
}
