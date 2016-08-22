package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleUtil}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.caches.CachesUtil
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.{ScPackageContainer, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScPackageLike, ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceExpression, ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ProjectPsiElementExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
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
        AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, false, true)
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

  def functionArrow(project: Project): String = {
    val useUnicode = ScalaCodeStyleSettings.getInstance(project).REPLACE_CASE_ARROW_WITH_UNICODE_CHAR
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
      s.annotations.exists {
        case annot => Set("scala.reflect.BooleanBeanProperty", "reflect.BooleanBeanProperty",
          "BooleanBeanProperty", "scala.beans.BooleanBeanProperty", "beans.BooleanBeanProperty").
                contains(annot.typeElement.getText.replace(" ", ""))
      }
    } else {
      s.hasAnnotation("scala.reflect.BooleanBeanProperty") ||
        s.hasAnnotation("scala.beans.BooleanBeanProperty")
    }
  }

  def isBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    if (noResolve) {
      s.annotations.exists {
        case annot => Set("scala.reflect.BeanProperty", "reflect.BeanProperty",
          "BeanProperty", "scala.beans.BeanProperty", "beans.BeanProperty").
                contains(annot.typeElement.getText.replace(" ", ""))
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

  def cachedDeepIsInheritor(clazz: PsiClass, base: PsiClass): Boolean = {
    val manager = ScalaPsiManager.instance(clazz.getProject)
    manager.cachedDeepIsInheritor(clazz, base)
  }

  def lastChildElementOrStub(element: PsiElement): PsiElement = {
    element match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null =>
        val stub = st.getStub
        val children = stub.getChildrenStubs
        if (children.size() == 0) element.getLastChild
        else children.get(children.size() - 1).getPsi
      case _ => element.getLastChild
    }
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
  def tuplizy(s: Seq[Expression], scope: GlobalSearchScope, manager: PsiManager, place: PsiElement): Option[Seq[Expression]] = {
    s match {
      case Seq() =>
        // object A { def foo(a: Any) = ()}; A foo () ==>> A.foo(()), or A.foo() ==>> A.foo( () )
        Some(Seq(new Expression(Unit, place)))
      case _ =>
        val exprTypes: Seq[ScType] =
          s.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)).map {
            case (res, _) => res.getOrAny
          }
        val qual = "scala.Tuple" + exprTypes.length
        val tupleClass = ScalaPsiManager.instance(manager.getProject).getCachedClass(scope, qual).orNull
        if (tupleClass == null) None
        else
          Some(Seq(new Expression(ScParameterizedType(ScDesignatorType(tupleClass), exprTypes), place)))
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
                              lastParent: PsiElement, typeResult: => TypeResult[ScType]): Boolean = {
    val subst = state.get(ScSubstitutor.key).toOption.getOrElse(ScSubstitutor.empty)
    lastParent match {
      case _: ScImportStmt =>
        typeResult match {
          case Success(t, _) =>
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
  *Pick all type parameters by method maps them to the appropriate type arguments, if they are
   */
  def inferMethodTypesArgs(fun: PsiMethod, classSubst: ScSubstitutor)
                          (implicit typeSystem: TypeSystem = fun.typeSystem): ScSubstitutor = {
    (fun match {
      case fun: ScFunction => fun.typeParameters
      case fun: PsiMethod => fun.getTypeParameters.toSeq
    }).foldLeft(ScSubstitutor.empty) {
      (subst, tp) => subst.bindT(tp.nameAndId,
        UndefinedType(TypeParameterType(tp, Some(classSubst)), 1))
    }
  }

  def findImplicitConversion(e: ScExpression, refName: String, ref: PsiElement, processor: BaseProcessor,
                             noImplicitsForArgs: Boolean)
                            (implicit typeSystem: TypeSystem): Option[ImplicitResolveResult] = {
    lazy val funType = ScalaPsiManager.instance(e.getProject)
      .getCachedClass("scala.Function1", e.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
      .collect {
        case cl: ScTrait =>
          ScParameterizedType(ScalaType.designator(cl),
            cl.typeParameters.map(p => UndefinedType(TypeParameterType(p), 1)))
      }.collect {
      case p: ScParameterizedType => p
    }

    def specialExtractParameterType(rr: ScalaResolveResult): (Option[ScType], Seq[TypeParameter]) = {
      def inner(tp: ScType) = {
        tp match {
          case f@FunctionType(_, _) => Some(f)
          case _ =>
            funType match {
              case Some(ft) =>
                val conformance = tp.conforms(ft, new ScUndefinedSubstitutor())
                if (conformance._1) {
                  conformance._2.getSubstitutor match {
                    case Some(subst) => Some(subst.subst(ft).removeUndefines())
                    case _ => None
                  }
                } else None
              case _ => None
            }
        }
      }
      val typeParams = rr.unresolvedTypeParameters match {
        case Some(tParams) if tParams.nonEmpty => tParams
        case _ => Seq.empty
      }
      (inner(InferUtil.extractImplicitParameterType(rr)), typeParams)
    }

    //TODO! remove this after find a way to improve implicits according to compiler.
    val isHardCoded = refName == "+" &&
      e.getTypeWithoutImplicits().map(_.isInstanceOf[ValType]).getOrElse(false)
    val kinds = processor.kinds
    var implicitMap: Seq[ScalaResolveResult] = Seq.empty
    def checkImplicits(secondPart: Boolean, noApplicability: Boolean, withoutImplicitsForArgs: Boolean = noImplicitsForArgs) {
      def args = processor match {
        case _ if !noImplicitsForArgs => Seq.empty
        case m: MethodResolveProcessor => m.argumentClauses.flatMap(_.map(
          _.getTypeAfterImplicitConversion(checkImplicits = false, isShape = m.isShapeResolve, None)._1.getOrAny
        ))
        case _ => Seq.empty
      }
      val exprType = ImplicitCollector.exprType(e, fromUnder = false).getOrElse(return)
      if (exprType.equiv(Nothing)) return //do not proceed with nothing type, due to performance problems.
      val convertible = new ImplicitCollector(e,
        FunctionType(Any, Seq(exprType))(e.getProject, e.getResolveScope),
        FunctionType(exprType, args)(e.getProject, e.getResolveScope), None, true, true,
          predicate = Some((rr, subst) => {
            ProgressManager.checkCanceled()
            specialExtractParameterType(rr) match {
              case (Some(FunctionType(tp, _)), _) =>
                if (!isHardCoded || !tp.isInstanceOf[ValType]) {
                  val newProc = new ResolveProcessor(kinds, ref, refName)
                  newProc.processType(tp, e, ResolveState.initial)
                  val res = newProc.candidatesS.nonEmpty
                  if (!noApplicability && res && processor.isInstanceOf[MethodResolveProcessor]) {
                    val mrp = processor.asInstanceOf[MethodResolveProcessor]
                    val newProc = new MethodResolveProcessor(ref, refName, mrp.argumentClauses, mrp.typeArgElements,
                      rr.element match {
                        case fun: ScFunction if fun.hasTypeParameters => fun.typeParameters.map(TypeParameter(_))
                        case _ => Seq.empty
                      }, kinds,
                      mrp.expectedOption, mrp.isUnderscore, mrp.isShapeResolve, mrp.constructorResolve, noImplicitsForArgs = withoutImplicitsForArgs)
                    newProc.processType(tp, e, ResolveState.initial)
                    val candidates = newProc.candidatesS.filter(_.isApplicable())
                    if (candidates.nonEmpty) {
                      rr.element match {
                        case fun: ScFunction if fun.hasTypeParameters =>
                          val newRR = candidates.iterator.next()
                          newRR.resultUndef match {
                            case Some(undef) =>
                              undef.getSubstitutor match {
                                case Some(uSubst) =>
                                  Some(rr.copy(subst = newRR.substitutor.followed(uSubst),
                                    implicitParameterType = rr.implicitParameterType.map(uSubst.subst)),
                                    subst.followed(uSubst))
                                case _ => Some(rr, subst)
                              }
                            case _ => Some(rr, subst)
                          }
                        case _ => Some(rr, subst)
                      }
                    } else None
                  } else if (res) Some(rr, subst)
                  else None
                } else None
              case _ => None
            }
          }))
      implicitMap = convertible.collect()
    }
    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    checkImplicits(secondPart = false, noApplicability = false)
    if (implicitMap.size > 1) {
      val oldMap = implicitMap
      checkImplicits(secondPart = false, noApplicability = false, withoutImplicitsForArgs = true)
      if (implicitMap.isEmpty) implicitMap = oldMap
    } else if (implicitMap.isEmpty) {
      checkImplicits(secondPart = true, noApplicability = false)
      if (implicitMap.size > 1) {
        val oldMap = implicitMap
        checkImplicits(secondPart = false, noApplicability = false, withoutImplicitsForArgs = true)
        if (implicitMap.isEmpty) implicitMap = oldMap
      } else if (implicitMap.isEmpty) checkImplicits(secondPart = false, noApplicability = true)
    }
    if (implicitMap.length == 1) {
      val rr = implicitMap.head
      specialExtractParameterType(rr) match {
        case (Some(FunctionType(tp, _)), typeParams) =>
          Some(ImplicitResolveResult(tp, rr.getElement, rr.importsUsed, rr.substitutor, ScSubstitutor.empty, isFromCompanion = false, //todo: from companion parameter
            unresolvedTypeParameters = typeParams))
        case _ => None
      }
    } else None
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

  @tailrec
  def findCall(place: PsiElement): Option[ScMethodCall] = {
    place.getContext match {
      case call: ScMethodCall => Some(call)
      case p: ScParenthesisedExpr => findCall(p)
      case g: ScGenericCall => findCall(g)
      case _ => None
    }
  }

  def approveDynamic(tp: ScType, project: Project, scope: GlobalSearchScope)
                    (implicit typeSystem: TypeSystem): Boolean = {
    val cachedClass = ScalaPsiManager.instance(project).getCachedClass(scope, "scala.Dynamic").orNull
    if (cachedClass == null) return false
    val dynamicType = ScDesignatorType(cachedClass)
    tp.conforms(dynamicType)
  }

  def processTypeForUpdateOrApplyCandidates(call: MethodInvocation, tp: ScType, isShape: Boolean,
                                            isDynamic: Boolean)
                                           (implicit typeSystem: TypeSystem): Array[ScalaResolveResult] = {
    val isUpdate = call.isUpdateCall
    val methodName =
      if (isDynamic) ResolvableReferenceExpression.getDynamicNameForMethodInvocation(call)
      else if (isUpdate) "update"
      else "apply"
    val args: Seq[ScExpression] = call.argumentExpressions ++ (
            if (isUpdate) call.getContext.asInstanceOf[ScAssignStmt].getRExpression match {
              case Some(x) => Seq[ScExpression](x)
              case None =>
                Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                  call.getManager)) //we can't to not add something => add Nothing expression
            }
            else Seq.empty)
    val (expr, exprTp, typeArgs: Seq[ScTypeElement]) = call.getEffectiveInvokedExpr match {
      case gen: ScGenericCall =>
        // The type arguments are for the apply/update method, separate them from the referenced expression. (SCL-3489)
        val referencedType = gen.referencedExpr.getNonValueType(TypingContext.empty).getOrNothing
        referencedType match {
          case _: ScTypePolymorphicType => //that means that generic call is important here
            (gen, gen.getType(TypingContext.empty).getOrNothing, Seq.empty)
          case _ =>
            (gen.referencedExpr, gen.referencedExpr.getType(TypingContext.empty).getOrNothing, gen.arguments)
        }
      case expression => (expression, tp, Seq.empty)
    }
    val invoked = call.getInvokedExpr
    val typeParams = invoked.getNonValueType(TypingContext.empty).map {
      case ScTypePolymorphicType(_, tps) => tps
      case _ => Seq.empty
    }.getOrElse(Seq.empty)
    val emptyStringExpression = ScalaPsiElementFactory.createExpressionFromText("\"\"", call.getManager)
    val processor = new MethodResolveProcessor(expr, methodName, if (!isDynamic) args :: Nil
      else List(List(emptyStringExpression), args), typeArgs, typeParams,
      isShapeResolve = isShape, enableTupling = true, isDynamic = isDynamic)
    var candidates: Set[ScalaResolveResult] = Set.empty
    exprTp match {
      case ScTypePolymorphicType(internal, typeParam) if typeParam.nonEmpty &&
        !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[UndefinedType] =>
        if (!isDynamic || approveDynamic(internal, call.getProject, call.getResolveScope)) {
          val state: ResolveState = ResolveState.initial().put(BaseProcessor.FROM_TYPE_KEY, internal)
          processor.processType(internal, call.getEffectiveInvokedExpr, state)
        }
        candidates = processor.candidatesS
      case _ =>
    }
    if (candidates.isEmpty && (!isDynamic || approveDynamic(exprTp.inferValueType, call.getProject, call.getResolveScope))) {
      val state: ResolveState = ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, exprTp.inferValueType)
      processor.processType(exprTp.inferValueType, call.getEffectiveInvokedExpr, state)
      candidates = processor.candidatesS
    }

    if (!isDynamic && candidates.forall(!_.isApplicable())) {
      processor.resetPrecedence()
      //should think about implicit conversions
      findImplicitConversion(expr, methodName, call, processor, noImplicitsForArgs = candidates.nonEmpty) match {
        case Some(res) =>
          ProgressManager.checkCanceled()
          val function = res.element
          var state = ResolveState.initial.put(ImportUsed.key, res.importUsed).
            put(CachesUtil.IMPLICIT_FUNCTION, function)
          res.getClazz match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          state = state.put(BaseProcessor.FROM_TYPE_KEY, res.tp)
          state = state.put(BaseProcessor.UNRESOLVED_TYPE_PARAMETERS_KEY, res.unresolvedTypeParameters)
          processor.processType(res.getTypeWithDependentSubstitutor, expr, state)
        case _ =>
      }
      candidates = processor.candidatesS
    }
    candidates.toArray
  }

  def processTypeForUpdateOrApply(tp: ScType, call: MethodInvocation, isShape: Boolean)
                                 (implicit typeSystem: TypeSystem):
      Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[ScalaResolveResult])] = {

    def checkCandidates(withDynamic: Boolean = false): Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[ScalaResolveResult])] = {
      val candidates: Array[ScalaResolveResult] = processTypeForUpdateOrApplyCandidates(call, tp, isShape, isDynamic = withDynamic)
      PartialFunction.condOpt(candidates) {
        case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
          def update(tp: ScType): ScType = {
            if (r.isDynamic) ResolvableReferenceExpression.getDynamicReturn(tp)
            else tp
          }
          val res = fun match {
            case fun: ScFun => (update(s.subst(fun.polymorphicType)), r.importsUsed, r.implicitFunction, Some(r))
            case fun: ScFunction => (update(s.subst(fun.polymorphicType())), r.importsUsed, r.implicitFunction, Some(r))
            case meth: PsiMethod => (update(ResolveUtils.javaPolymorphicType(meth, s, call.getResolveScope)),
              r.importsUsed, r.implicitFunction, Some(r))
          }
        call.getInvokedExpr.getNonValueType(TypingContext.empty) match {
          case Success(ScTypePolymorphicType(_, typeParams), _) =>
            res.copy(_1 = res._1 match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2))
              case _ => ScTypePolymorphicType(res._1, typeParams)
            })
          case _ => res
        }
      }
    }

    checkCandidates(withDynamic = false).orElse(checkCandidates(withDynamic = true))
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
    tp match {
      case tp@ScTypePolymorphicType(internal, typeParameters) =>
        def hasBadLinks(tp: ScType, ownerPtp: PsiTypeParameter): Option[ScType] = {
          var res: Option[ScType] = Some(tp)
          tp.recursiveUpdate {tp =>
            tp match {
              case t: TypeParameterType =>
                if (typeParameters.exists {
                  case TypeParameter(_, _, _, ptp) if ptp == t.psiTypeParameter && ptp.getOwner != ownerPtp.getOwner => true
                  case _ => false
                }) res = None
              case _ =>
            }
            (false, tp)
          }
          res
        }

        def clearBadLinks(tps: Seq[TypeParameter]): Seq[TypeParameter] = tps.map {
          case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
            TypeParameter(
              clearBadLinks(parameters),
              new Suspension(hasBadLinks(lowerType.v, psiTypeParameter).getOrElse(Nothing)),
              new Suspension(hasBadLinks(upperType.v, psiTypeParameter).getOrElse(Any)),
              psiTypeParameter)
        }

        ScTypePolymorphicType(internal, clearBadLinks(typeParameters))(tp.typeSystem)
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
      case p: ScParenthesisedExpr => p.expr match {case Some(x) => isAnonymousExpression(x) case _ => (-1, expr)}
      case f: ScFunctionExpr =>  (f.parameters.length, expr)
      case _ => (-1, expr)
    }
  }

  def isAnonExpression(expr: ScExpression): Boolean = isAnonymousExpression(expr)._1 >= 0

  def getModule(element: PsiElement): Module = {
    val index: ProjectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    index.getModuleForFile(element.getContainingFile.getVirtualFile)
  }

  def collectImplicitObjects(_tp: ScType, project: Project, scope: GlobalSearchScope)
                            (implicit typeSystem: TypeSystem = project.typeSystem): Seq[ScType] = {
    val tp = _tp.removeAliasDefinitions()
    val implicitObjectsCache = ScalaPsiManager.instance(project).collectImplicitObjectsCache
    val cacheKey = (tp, scope)
    var cachedResult = implicitObjectsCache.get(cacheKey)
    if (cachedResult != null) return cachedResult

    val visited: mutable.HashSet[ScType] = new mutable.HashSet[ScType]()
    val parts: mutable.Queue[ScType] = new mutable.Queue[ScType]

    def collectParts(tp: ScType) {
      ProgressManager.checkCanceled()
      if (visited.contains(tp)) return
      visited += tp
      tp.isAliasType match {
        case Some(AliasType(_, _, upper)) => upper.foreach(collectParts)
        case _                            =>
      }

      def collectSupers(clazz: PsiClass, subst: ScSubstitutor) {
        clazz match {
          case td: ScTemplateDefinition =>
            td.superTypes.foreach {
              tp => collectParts(subst.subst(tp))
            }
          case clazz: PsiClass          =>
            clazz.getSuperTypes.foreach {
              tp =>
                val stp = tp.toScType()
                collectParts(subst.subst(stp))
            }
        }
      }

      tp match {
        case ScDesignatorType(v: ScBindingPattern)          => v.getType(TypingContext.empty).foreach(collectParts)
        case ScDesignatorType(v: ScFieldId)                 => v.getType(TypingContext.empty).foreach(collectParts)
        case ScDesignatorType(p: ScParameter)               => p.getType(TypingContext.empty).foreach(collectParts)
        case ScCompoundType(comps, _, _)                 => comps.foreach(collectParts)
        case ParameterizedType(a: ScAbstractType, args) =>
          collectParts(a)
          args.foreach(collectParts)
        case p@ParameterizedType(des, args) =>
          p.extractClassType(project) match {
            case Some((clazz, subst)) =>
              parts += des
              collectParts(des)
              args.foreach(collectParts)
              collectSupers(clazz, subst)
            case _ =>
              collectParts(des)
              args.foreach(collectParts)
          }
        case j: JavaArrayType =>
          val parameterizedType = j.getParameterizedType(project, scope)
          collectParts(parameterizedType.getOrElse(return))
        case proj@ScProjectionType(projected, _, _) =>
          collectParts(projected)
          proj.actualElement match {
            case v: ScBindingPattern => v.getType(TypingContext.empty).map(proj.actualSubst.subst).foreach(collectParts)
            case v: ScFieldId        => v.getType(TypingContext.empty).map(proj.actualSubst.subst).foreach(collectParts)
            case v: ScParameter      => v.getType(TypingContext.empty).map(proj.actualSubst.subst).foreach(collectParts)
            case _                   =>
          }
          tp.extractClassType(project) match {
            case Some((clazz, subst)) =>
              parts += tp
              collectSupers(clazz, subst)
            case _          =>
          }
        case ScAbstractType(_, _, upper) =>
          collectParts(upper)
        case ScExistentialType(quant, _) => collectParts(quant)
        case TypeParameterType(_, _, upper, _) => collectParts(upper.v)
        case _                           =>
          tp.extractClassType(project) match {
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
          case Any =>
          case tp: StdType if Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char").contains(tp.name) =>
            ScalaPsiManager.instance(project).
              getCachedClass("scala." + tp.name, scope, ClassCategory.OBJECT)
              .collect {
                case o: ScObject => o
              }.foreach { o =>
              addResult(o.qualifiedName, ScDesignatorType(o))
            }
          case ScDesignatorType(ta: ScTypeAliasDefinition) => collectObjects(ta.aliasedType.getOrAny)
          case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
            collectObjects(p.actualSubst.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny))
          case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
            collectObjects(genericSubst.subst(ta.aliasedType.getOrAny))
          case ParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(p.actualElement.asInstanceOf[ScTypeAliasDefinition].typeParameters.map(_.nameAndId), args)
            val s = p.actualSubst.followed(genericSubst)
            collectObjects(s.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny))
          case _ =>
            for {
              (clazz: PsiClass, subst: ScSubstitutor) <- tp.extractClassType(project)
              if !visited.contains(clazz)
            } {
              clazz match {
                case o: ScObject => addResult(o.qualifiedName, tp)
                case _           =>
                  getCompanionModule(clazz) match {
                    case Some(obj: ScObject) =>
                      tp match {
                        case ScProjectionType(proj, _, s) =>
                          addResult(obj.qualifiedName, ScProjectionType(proj, obj, s))
                        case ParameterizedType(ScProjectionType(proj, _, s), _) =>
                          addResult(obj.qualifiedName, ScProjectionType(proj, obj, s))
                        case _ => addResult(obj.qualifiedName, ScDesignatorType(obj))
                      }
                    case _ =>
                  }
              }
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
        case scp: ScParameter => scp.getType(TypingContext.empty).getOrNothing
        case p: PsiParameter =>
          val treatJavaObjectAsAny = p.parentsInFile.findByType(classOf[PsiClass]) match {
            case Some(cls) if cls.qualifiedName == "java.lang.Object" => true // See SCL-3036
            case _ => false
          }
          p.paramType(treatJavaObjectAsAny = treatJavaObjectAsAny)
      }
    )
  }

  /**
   *  This method doesn't collect things like
   *  val a: Type = b //after implicit convesion
   *  Main task for this method to collect imports used in 'for' statemnts.
   */
  def getExprImports(z: ScExpression): Set[ImportUsed] = {
    var res: Set[ImportUsed] = Set.empty
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitExpression(expr: ScExpression) {
        //Implicit parameters
        expr.findImplicitParameters match {
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
            for (rr <- ref.multiResolve(false) if rr.isInstanceOf[ScalaResolveResult]) {
              res = res ++ rr.asInstanceOf[ScalaResolveResult].importsUsed
            }
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
    CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
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
    import scala.collection.JavaConversions._
    (getParents(before, topLevel.orNull, contextParents = true) zip getParents(after, topLevel.orNull, contextParents = true)
      dropWhile { case (a, b) => a == b }).headOption.exists {
      case (beforeAncestor, afterAncestor) =>
        val topChildren: Seq[PsiElement] = beforeAncestor.getContext match {
          case s: StubBasedPsiElement[_] if s.getStub != null =>
            val stub = s.getStub.asInstanceOf[StubElement[_ <: PsiElement]]
            stub.getChildrenStubs.map(_.getPsi)
          case other => other.getChildren
        }
        topChildren.indexOf(beforeAncestor) <= topChildren.indexOf(afterAncestor)
    }
  }

  def getParents(elem: PsiElement, topLevel: PsiElement, contextParents: Boolean = false): List[PsiElement] = {
    @tailrec
    def inner(parent: PsiElement, k: List[PsiElement] => List[PsiElement]): List[PsiElement] = {
      if (parent != topLevel && parent != null)
        inner(if (contextParents) parent.getContext else parent.getParent, {l => parent :: k(l)})
      else k(Nil)
    }
    inner(elem, (l: List[PsiElement]) => l)
  }

  def getFirstStubOrPsiElement(elem: PsiElement): PsiElement = {
    elem match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null =>
        val stub = st.getStub
        val childrenStubs = stub.getChildrenStubs
        if (childrenStubs.size() > 0) childrenStubs.get(0).getPsi
        else null
      case file: PsiFileImpl if file.getStub != null =>
        val stub = file.getStub
        val childrenStubs = stub.getChildrenStubs
        if (childrenStubs.size() > 0) childrenStubs.get(0).getPsi
        else null
      case _ => elem.getFirstChild
    }
  }

  def getPrevStubOrPsiElement(elem: PsiElement): PsiElement = {
    def workWithStub(stub: StubElement[_ <: PsiElement]): PsiElement = {
      val parent = stub.getParentStub
      if (parent == null) return null

      val children = parent.getChildrenStubs
      val index = children.indexOf(stub)
      if (index == -1) {
        elem.getPrevSibling
      } else if (index == 0) {
        null
      } else {
        children.get(index - 1).getPsi
      }
    }
    elem match {
      case st: ScalaStubBasedElementImpl[_] =>
        val stub = st.getStub
        if (stub != null) return workWithStub(stub)
      case file: PsiFileImpl =>
        val stub = file.getStub
        if (stub != null) return workWithStub(stub)
      case _ =>
    }
    elem.getPrevSibling
  }

  def isLValue(elem: PsiElement): Boolean = elem match {
    case e: ScExpression => e.getParent match {
      case as: ScAssignStmt => as.getLExpression eq e
      case _ => false
    }
    case _ => false
  }

  def getNextStubOrPsiElement(elem: PsiElement): PsiElement = {
    elem match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null =>
        val stub = st.getStub
        val parent = stub.getParentStub
        if (parent == null) return null

        val children = parent.getChildrenStubs
        val index = children.indexOf(stub)
        if (index == -1) {
          elem.getNextSibling
        } else if (index >= children.size - 1) {
          null
        } else {
          children.get(index + 1).getPsi
        }
      case _ => elem.getNextSibling
    }
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

  def typesCallSubstitutor(tp: Seq[(String, Long)], typeArgs: Seq[ScType]): ScSubstitutor = {
    val map = new collection.mutable.HashMap[(String, Long), ScType]
    for (i <- 0 until math.min(tp.length, typeArgs.length)) {
      map += ((tp(i), typeArgs(i)))
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
  }

  def genericCallSubstitutor(tp: Seq[(String, Long)], typeArgs: Seq[ScTypeElement]): ScSubstitutor = {
    val map = new collection.mutable.HashMap[(String, Long), ScType]
    for (i <- 0 until Math.min(tp.length, typeArgs.length)) {
      map += ((tp(tp.length - 1 - i), typeArgs(typeArgs.length - 1 - i).getType(TypingContext.empty).getOrAny))
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
  }

  def genericCallSubstitutor(tp: Seq[(String, Long)], gen: ScGenericCall): ScSubstitutor = {
    val typeArgs: Seq[ScTypeElement] = gen.arguments
    genericCallSubstitutor(tp, typeArgs)
  }

  def namedElementSig(x: PsiNamedElement)
                     (implicit typeSystem: TypeSystem): Signature =
    new Signature(x.name, Seq.empty, 0, ScSubstitutor.empty, x)

  def superValsSignatures(x: PsiNamedElement, withSelfType: Boolean = false)
                         (implicit typeSystem: TypeSystem = x.typeSystem): Seq[Signature] = {
    val empty = Seq.empty
    val typed = x match {case x: ScTypedDefinition => x case _ => return empty}
    val clazz: ScTemplateDefinition = nameContext(typed) match {
      case e @ (_: ScValue | _: ScVariable | _:ScObject) if e.getParent.isInstanceOf[ScTemplateBody] ||
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
      case Some(node) if !withSelfType || node.info.namedElement == x => node.supers.map {_.info}
      case Some(node) =>
        node.supers.map { _.info }.filter { _.namedElement != x } :+ node.info
      case None =>
        //this is possible case: private member of library source class.
        //Problem is that we are building signatures over decompiled class.
        Seq.empty
    }


    val beanMethods = typed.getBeanMethods
    beanMethods.foreach {method =>
      val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)._1
      (sigs.get(new PhysicalSignature(method, ScSubstitutor.empty)): @unchecked) match {
        //partial match
        case Some(node) if !withSelfType || node.info.namedElement == method => res ++= node.supers.map {_.info}
        case Some(node) =>
          res +:= node.info
          res ++= node.supers.map { _.info }.filter { _.namedElement != method }
        case None =>
      }
    }

    res

  }

  def superTypeMembers(element: PsiNamedElement, withSelfType: Boolean = false)
                      (implicit typeSystem: TypeSystem = element.typeSystem): Seq[PsiNamedElement] = {
    superTypeMembersAndSubstitutors(element, withSelfType).map(_.info)
  }

  def superTypeMembersAndSubstitutors(element: PsiNamedElement, withSelfType: Boolean = false)
                                     (implicit typeSystem: TypeSystem): Seq[TypeDefinitionMembers.TypeNodes.Node] = {
    val clazz: ScTemplateDefinition = nameContext(element) match {
      case e @ (_: ScTypeAlias | _: ScTrait | _: ScClass) if e.getParent.isInstanceOf[ScTemplateBody] => e.asInstanceOf[ScMember].containingClass
      case _ => return Seq.empty
    }
    if (clazz == null) return Seq.empty
    val types = if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(clazz) else TypeDefinitionMembers.getTypes(clazz)
    val sigs = types.forName(element.name)._1
    (sigs.get(element): @unchecked) match {
      //partial match
      case Some(x) if !withSelfType || x.info == element => x.supers
      case Some(x) =>
        x.supers.filter { _.info != element } :+ x
      case None =>
        //this is possible case: private member of library source class.
        //Problem is that we are building types over decompiled class.
        Seq.empty
    }
  }

  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias | _: ScParameter | _: PsiMethod | _: PsiField |
                _: ScCaseClause | _: PsiClass | _: PsiPackage | _: ScGenerator | _: ScEnumerator | _: ScObject => true
        case _ => false
      }
    }
    if (isAppropriatePsiElement(x)) return x
    while (parent != null && !isAppropriatePsiElement(parent)) parent = parent.getParent
    parent
  }

  object inNameContext {
    def unapply(x: PsiNamedElement): Option[PsiElement] = nameContext(x).toOption
  }

  def getEmptyModifierList(manager: PsiManager): PsiModifierList =
    new LightModifierList(manager, ScalaFileType.SCALA_LANGUAGE)

  def adjustTypes(element: PsiElement, addImports: Boolean = true, useTypeAliases: Boolean = true)
                 (implicit typeSystem: TypeSystem = element.typeSystem) {
    TypeAdjuster.adjustFor(Seq(element), addImports, useTypeAliases)
  }

  def getMethodPresentableText(method: PsiMethod, subst: ScSubstitutor = ScSubstitutor.empty): String = {
    method match {
      case method: ScFunction =>
        ScalaElementPresentation.getMethodPresentableText(method, fast = false, subst)
      case _ =>
        val PARAM_OPTIONS: Int = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER
        PsiFormatUtil.formatMethod(method, getPsiSubstitutor(subst, method.getProject, method.getResolveScope),
          PARAM_OPTIONS | PsiFormatUtilBase.SHOW_PARAMETERS, PARAM_OPTIONS)
    }
  }

  def getModifiersPresentableText(modifiers: ScModifierList): String = {
    if (modifiers == null) return ""
    val buffer = new StringBuilder("")
    modifiers match {
      case st: StubBasedPsiElement[_] if st.getStub != null =>
        for (modifier <- st.getStub.asInstanceOf[ScModifiersStub].getModifiers) buffer.append(modifier + " ")
      case _ =>
        for (modifier <- modifiers.getNode.getChildren(null) if !isLineTerminator(modifier.getPsi)) buffer.append(modifier.getText + " ")
    }
    buffer.toString()
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => true
      case _ => false
    }
  }

  def allMethods(clazz: PsiClass): Iterable[PhysicalSignature] =
    TypeDefinitionMembers.getSignatures(clazz).allFirstSeq().flatMap(_.filter {
      case (_, n) => n.info.isInstanceOf[PhysicalSignature]}).
            map { case (_, n) => n.info.asInstanceOf[PhysicalSignature] }

  def getMethodsForName(clazz: PsiClass, name: String): Seq[PhysicalSignature] = {
    for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(clazz).forName(name)._1
         if clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static")) yield n
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "apply")
  }

  def getUnapplyMethods(clazz: PsiClass)
                       (implicit typeSystem: TypeSystem): Seq[PhysicalSignature] = {
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

  /**
    *  For one classOf use PsiTreeUtil.getParenteOfType instead
    */
  def getParentOfType(element: PsiElement, clazz: Class[_ <: PsiElement]): PsiElement = {
    getParentOfType(element, false, clazz)
  }
  /**
   *  For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, classes: Class[_ <: PsiElement]*): PsiElement = {
    getParentOfType(element, false, classes: _*)
  }

  /**
   * For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, strict: Boolean, classes: Class[_ <: PsiElement]*): PsiElement = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null
      element.getParent
    }
    while (el != null && !classes.exists(_.isInstance(el))) el = el.getParent
    el
  }

  @tailrec
  def getParentWithProperty(element: PsiElement, strict: Boolean, property: PsiElement => Boolean): Option[PsiElement] = {
    if (element == null) None
    else if (!strict && property(element)) Some(element)
    else getParentWithProperty(element.getParent, strict = false, property)
  }

  @tailrec
  def getParent(element: PsiElement, level: Int): Option[PsiElement] =
    if (level == 0) Some(element) else
    if (element.parent.isEmpty) None
    else getParent(element.getParent, level - 1)

  def contextOfType[T <: PsiElement](element: PsiElement, strict: Boolean, clazz: Class[T]): T = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null.asInstanceOf[T]
      element.getContext
    }
    while (el != null && !clazz.isInstance(el)) el = el.getContext
    el.asInstanceOf[T]
  }

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

  def getCompanionModule(clazz: PsiClass): Option[ScTypeDefinition] = {
    getBaseCompanionModule(clazz) match {
      case Some(td) => Some(td)
      case _ =>
        clazz match {
          case x: ScTypeDefinition => x.fakeCompanionModule
          case _ => None
        }
    }
  }

  //Performance critical method
  def getBaseCompanionModule(clazz: PsiClass): Option[ScTypeDefinition] = {
    val (td, scope) = clazz match {
      case t: ScTypeDefinition if t.getContext != null => (t, t.getContext)
      case _ => return None
    }

    val name: String = td.name
    val tokenSet = ScalaTokenSets.typeDefinitions
    val arrayOfElements: Array[PsiElement] = scope match {
      case stub: StubBasedPsiElement[_] if stub.getStub != null =>
        stub.getStub.getChildrenByType(tokenSet, JavaArrayFactoryUtil.PsiElementFactory)
      case file: PsiFileImpl =>
        val stub = file.getStub
        if (stub != null) {
          file.getStub.getChildrenByType(tokenSet, JavaArrayFactoryUtil.PsiElementFactory)
        } else scope.getChildren
      case _ => scope.getChildren
    }
    td match {
      case _: ScClass | _: ScTrait =>
        var i = 0
        val length  = arrayOfElements.length
        while (i < length) {
          arrayOfElements(i) match {
            case obj: ScObject if obj.name == name => return Some(obj)
            case _ =>
          }
          i = i + 1
        }
        None
      case _: ScObject =>
        var i = 0
        val length  = arrayOfElements.length
        while (i < length) {
          arrayOfElements(i) match {
            case c: ScClass if c.name == name => return Some(c)
            case t: ScTrait if t.name == name => return Some(t)
            case _ =>
          }
          i = i + 1
        }
        None
      case _ => None
    }
  }

  object FakeCompanionClassOrCompanionClass {
    def unapply(obj: ScObject): Option[PsiClass] = Option(obj.fakeCompanionClassOrCompanionClass)
  }

  def withCompanionSearchScope(clazz: PsiClass): SearchScope = {
    getBaseCompanionModule(clazz) match {
      case Some(companion) => new LocalSearchScope(clazz).union(new LocalSearchScope(companion))
      case None => new LocalSearchScope(clazz)
    }
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
        case j if j.getLanguage.isInstanceOf[JavaLanguage] => true
        case _ => false
      }
    }

    nameContext(o) match {
      case member: PsiMember => hasStablePathInner(member)
      case _: ScPackaging | _: PsiPackage => true
      case _ => false
    }
  }

  def getPsiSubstitutor(subst: ScSubstitutor, project: Project, scope: GlobalSearchScope): PsiSubstitutor = {
    case class PseudoPsiSubstitutor(substitutor: ScSubstitutor) extends PsiSubstitutor {
      def putAll(parentClass: PsiClass, mappings: Array[PsiType]): PsiSubstitutor = PsiSubstitutor.EMPTY

      def isValid: Boolean = true

      def put(classParameter: PsiTypeParameter, mapping: PsiType): PsiSubstitutor = PsiSubstitutor.EMPTY

      def getSubstitutionMap: java.util.Map[PsiTypeParameter, PsiType] = new java.util.HashMap[PsiTypeParameter, PsiType]()

      def substitute(`type` : PsiType): PsiType = {
        implicit val typeSystem = project.typeSystem
        substitutor.subst(`type`.toScType()).toPsiType(project, scope)
      }

      def substitute(typeParameter: PsiTypeParameter): PsiType = {
        substitutor.subst(TypeParameterType(typeParameter, Some(substitutor))).toPsiType(project, scope)
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
          if (funTypeToken != null &&  element.getTextOffset < funTypeToken.getTextOffset) false
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
      if (parent.lOp == from) {
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
      if (from == parent.lOp) false
      else {
        parent.operation.bind() match {
          case Some(resolveResult: ResolveResult) =>
            val startInParent: Int = from.getStartOffsetInParent
            val endInParent: Int = startInParent + from.getTextLength
            val parentText = parent.getText
            val modifiedParentText = parentText.substring(0, startInParent) + from.getText + parentText.substring(endInParent)
            val modifiedParent = ScalaPsiElementFactory.createExpressionFromText(modifiedParentText, parent.getContext)
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
            val dummyFile = ScalaPsiElementFactory.createScalaFile(text, expr.getManager)
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
        case (_: ScGuard                               , _: ScMatchStmt) => true
        case _ if !parent.isInstanceOf[ScExpression] => false
        case _ if expr.getText == "_" => false
        case (_: ScTuple | _: ScBlock | _: ScXmlExpr   , _) => false
        case (infix: ScInfixExpr                       , tuple: ScTuple) => tupleInInfixNeedParentheses(infix, from, tuple)
        case (_: ScSugarCallExpr |
              _: ScReferenceExpression                 , elem: PsiElement) if ScUnderScoreSectionUtil.isUnderscoreFunction(elem) => true
        case (_                                        , _: ScReferenceExpression | _: ScMethodCall |
                                                         _: ScGenericCall | _: ScLiteral | _: ScTuple |
                                                         _: ScXmlExpr | _: ScParenthesisedExpr | _: ScUnitExpr |
                                                         _: ScThisReference | _: ScSuperReference) => false
        case (_: ScMethodCall | _: ScUnderscoreSection , _) => true
        case (_                                        , _: ScBlock) => false
        case (_: ScGenericCall                         , _) => true
        case (_: ScReferenceExpression                 , _: ScNewTemplateDefinition) =>
          val lastChar: Char = expr.getText.last
          lastChar != ')' && lastChar != '}' && lastChar != ']'
        case (_: ScReferenceExpression                 , _) => true
        case (_                                        , _: ScNewTemplateDefinition |
                                                         _: ScUnderscoreSection) => false
        case (_: ScPrefixExpr                          , _) => true
        case (_                                        , _: ScPrefixExpr) => false
        case (par: ScInfixExpr                         , child: ScInfixExpr) => infixInInfixParentheses(par, child)
        case (_                                        , _: ScInfixExpr) => false
        case (_: ScPostfixExpr | _: ScInfixExpr        , _) => true
        case (_                                        , _: ScPostfixExpr) => false
        case (_: ScTypedStmt | _: ScMatchStmt          , _) => true
        case _ => false
      }
    }
  }

  def isScope(element: PsiElement): Boolean = element match {
    case _: ScalaFile | _: ScBlock | _: ScTemplateBody | _: ScPackageContainer | _: ScParameters |
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
              .filter(_.allPatternsSimple)
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
      case ScAssignStmt(ResolvesTo(p: ScParameter), Some(_)) => Some(new Parameter(p))
      case _ =>
        exp.getParent match {
          case parenth: ScParenthesisedExpr => parameterOf(parenth)
          case named: ScAssignStmt => parameterOf(named)
          case block: ScBlock if block.statements == Seq(exp) => parameterOf(block)
          case ie: ScInfixExpr if exp == (if (ie.isLeftAssoc) ie.lOp else ie.rOp) =>
            ie.operation match {
              case ResolvesTo(f: ScFunction) => f.parameters.headOption.map(p => new Parameter(p))
              case ResolvesTo(method: PsiMethod) =>
                method.getParameterList.getParameters match {
                  case Array(p) => Some(new Parameter(p))
                  case _ => None
                }
              case _ => None
            }
          case (_: ScTuple) childOf (inf: ScInfixExpr) => fromMatchedParams(inf.matchedParameters)
          case args: ScArgumentExprList => fromMatchedParams(args.matchedParameters)
          case _ => None
        }
    }
  }

  /**
   * If `param` is a synthetic parameter with a corresponding real parameter, return Some(realParameter), otherwise None
   */
  def parameterForSyntheticParameter(param: ScParameter): Option[ScParameter] = {
    val fun = PsiTreeUtil.getParentOfType(param, classOf[ScFunction], true)

    def paramFromConstructor(td: ScClass) = td.constructor match {
      case Some(constr) => constr.parameters.find(p => p.name == param.name) // TODO multiple parameter sections.
      case _ => None
    }

    if (fun == null) {
      None
    } else if (fun.isSyntheticCopy) {
      fun.containingClass match {
        case td: ScClass if td.isCase => paramFromConstructor(td)
        case _ => None
      }
    } else if (fun.isSyntheticApply) {
      getCompanionModule(fun.containingClass) match {
        case Some(td: ScClass) if td.isCase => paramFromConstructor(td)
        case _ => None
      }
    } else None
  }

  def isReadonly(e: PsiElement): Boolean = {
    e match {
      case classParameter: ScClassParameter =>
        return classParameter.isVal
      case _ =>
    }

    if(e.isInstanceOf[ScParameter]) {
      return true
    }

    val parent = e.getParent

    if(parent.isInstanceOf[ScGenerator] ||
            parent.isInstanceOf[ScEnumerator] ||
            parent.isInstanceOf[ScCaseClause]) {
      return true
    }

    e.parentsInFile.takeWhile(!_.isScope).findByType(classOf[ScPatternDefinition]).isDefined
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
    import expr.typeSystem
    isCanonicalArg(expr) && parameterOf(expr).exists(p => FunctionType.isFunctionType(p.paramType))
  }

  object MethodValue {
    def unapply(expr: ScExpression): Option[PsiMethod] = {
      import expr.typeSystem
      if (!expr.expectedType(fromUnderscore = false).exists {
        case FunctionType(_, _) => true
        case expected if isSAMEnabled(expr) =>
          toSAMType(expected, expr.getResolveScope, expr.scalaLanguageLevelOrDefault).isDefined
        case _ => false
      }) {
        return None
      }
      expr match {
        case ref: ScReferenceExpression if !ref.getParent.isInstanceOf[MethodInvocation] => referencedMethod(ref, canBeParameterless = false)
        case gc: ScGenericCall if !gc.getParent.isInstanceOf[MethodInvocation] => referencedMethod(gc, canBeParameterless = false)
        case us: ScUnderscoreSection => us.bindingExpr.flatMap(referencedMethod(_, canBeParameterless = true))
        case ScMethodCall(invoked @(_: ScReferenceExpression | _: ScGenericCall | _: ScMethodCall), args)
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

  /** Creates a synthetic parameter clause based on view and context bounds */
  def syntheticParamClause(element: PsiNamedElement, paramClauses: ScParameters, classParam: Boolean, hasImplicit: Boolean): Option[ScParameterClause] =
  Option(element).collect {
    case parameterOwner: ScTypeParametersOwner if !hasImplicit => parameterOwner
  }.flatMap { parameterOwner =>
    def views(typeParameter: ScTypeParam) = typeParameter.viewTypeElement.map { typeElement =>
      val needParenthesis = typeElement match {
        case _: ScCompoundTypeElement |
             _: ScInfixTypeElement |
             _: ScFunctionalTypeElement |
             _: ScExistentialTypeElement => true
        case _ => false
      }
      val elementText = typeElement.getText.parenthesize(needParenthesis)
      s"${typeParameter.name} ${functionArrow(typeElement.getProject)} $elementText"
    }

    def bounds(typeParameter: ScTypeParam) = typeParameter.contextBoundTypeElement.map { typeElement =>
      s"${typeElement.getText}[${typeParameter.name}]"
    }

    val typeParameters = parameterOwner.typeParameters
    val maybeText = (typeParameters.flatMap(views) ++ typeParameters.flatMap(bounds)).zipWithIndex.map {
      case (text, index) => s"ev$$${index + 1}: $text"
    } match {
      case Seq() => None
      case seq => Some(seq.mkString("(implicit ", ", ", ")"))
    }

    val result = maybeText.map {
      def createClause =
        if (classParam) ScalaPsiElementFactory.createImplicitClassParamClauseFromTextWithContext _
        else ScalaPsiElementFactory.createImplicitClauseFromTextWithContext _
      createClause(_, element.getManager, paramClauses)
    }

    result.foreach { clause =>
      val typeElements = typeParameters.flatMap(_.viewTypeElement) ++
        typeParameters.flatMap(_.contextBoundTypeElement)

      clause.parameters.flatMap {
        _.typeElement.toSeq
      }.foreachWithIndex {
        case (typeElement, index) => typeElements(index).analog = typeElement
      }
    }

    result
  }

  def isPossiblyAssignment(ref: PsiReference): Boolean = isPossiblyAssignment(ref.getElement)

  //todo: fix it
  // This is a conservative approximation, we should really resolve the operation
  // to differentiate self assignment from calling a method whose name happens to be an assignment operator.
  def isPossiblyAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignStmt if assign.getLExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1 @ ScReferenceExpression.withQualifier(`elem`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }

  def availableImportAliases(position: PsiElement): Set[(ScReferenceElement, String)] = {
    def getSelectors(holder: ScImportsHolder): Set[(ScReferenceElement, String)] = Option(holder) map {
      _.getImportStatements
    } map {
      _ flatMap {
        _.importExprs
      } flatMap {
        _.selectors
      } filter {
        _.importedName != "_"
      } filter {
        _.reference != null
      } map { selector =>
        (selector.reference.asInstanceOf[ScReferenceElement], selector.importedName)
      } filter {
        case (reference, name) => reference.refName != name
      } toSet
    } getOrElse Set.empty

    if (position != null && position.getLanguage.getID != "Scala")
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
      val ref = ScalaPsiElementFactory.createReferenceFromText(text, position.getContext, position)
      val resolves = aliasRef.multiResolve(false)
      resolves.exists {
        case rr: ScalaResolveResult => ref.isReferenceTo(rr.element)
        case _ => false
      }
    }
    aliases.filter(_._1.getTextRange.getEndOffset < position.getTextOffset).filter(correctResolve).toSet
  }

  def importAliasFor(element: PsiElement, refPosition: PsiElement): Option[ScReferenceElement] = {
    val importAliases = availableImportAliases(refPosition)
    val suitableAliases = importAliases.collect {
      case (aliasRef, aliasName)
        if aliasRef.multiResolve(false).exists(rr => ScEquivalenceUtil.smartEquivalence(rr.getElement, element)) => aliasName
    }
    if (suitableAliases.nonEmpty) {
      val newRef: ScStableCodeReferenceElement = ScalaPsiElementFactory.createReferenceFromText(suitableAliases.head, refPosition.getManager)
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
          parent.addBefore(ScalaPsiElementFactory.createWhitespace(element.getManager), element)
        }
        if (!nextElement.isInstanceOf[PsiWhiteSpace]) {
          parent.addAfter(ScalaPsiElementFactory.createWhitespace(element.getManager), element)
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
        if (bind.getType(TypingContext.empty).toOption == instance.getType(TypingContext.empty).toOption) return Option(bind)
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

  private def addBefore[T <: PsiElement](element: T, parent: PsiElement, anchorOpt: Option[PsiElement]): T ={
    val anchor = anchorOpt match {
      case Some(a) => a
      case None =>
        val last = parent.getLastChild
        if (ScalaPsiUtil.isLineTerminator(last.getPrevSibling)) last.getPrevSibling
        else last
    }

    def addBefore(e: PsiElement) = parent.addBefore(e, anchor)
    def newLine: PsiElement = ScalaPsiElementFactory.createNewLineNode(element.getManager).getPsi

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
    val manager = member.getManager
    val modifierList = member.getModifierList
    if (newVisibility == "" || newVisibility == "public") {
      modifierList.accessModifier.foreach(_.delete())
      return
    }
    val newElem = ScalaPsiElementFactory.createModifierFromText(newVisibility, manager).getPsi
    modifierList.accessModifier match {
      case Some(mod) => mod.replace(newElem)
      case None =>
        if (modifierList.children.isEmpty) {
          modifierList.add(newElem)
        } else {
          val mod = modifierList.getFirstChild
          modifierList.addBefore(newElem, mod)
          modifierList.addBefore(ScalaPsiElementFactory.createWhitespace(manager), mod)
        }
    }
  }

  /**
   * @see https://github.com/non/kind-projector
   */
  def kindProjectorPluginEnabled(e: PsiElement): Boolean = {
    val plugins = e.module match {
      case Some(mod) => mod.scalaCompilerSettings.plugins
      case _ => ScalaCompilerConfiguration.instanceIn(e.getProject).defaultProfile.getSettings.plugins
    }
    plugins.exists(_.contains("kind-projector"))
  }

  /**
   * @see https://github.com/non/kind-projector
   */
  def kindProjectorPluginEnabled(p: Project): Boolean = {
    val modules = ModuleUtil.getModulesOfType(p, JavaModuleType.getModuleType)
    import collection.JavaConversions._
    modules.exists { mod =>
      mod.hasScala && mod.scalaCompilerSettings.plugins.exists(_.contains("kind-projector"))
    }
  }

  /**
    * Should we check if it's a Single Abstract Method?
    * In 2.11 works with -Xexperimental
    * In 2.12 works by default
    *
    * @return true if language level and flags are correct
    */
  def isSAMEnabled(e: PsiElement): Boolean = e.scalaLanguageLevel match {
    case Some(lang) if lang < Scala_2_11 => false
    case Some(lang) if lang == Scala_2_11 =>
      val settings = e.module match {
        case Some(module) => module.scalaCompilerSettings
        case None => ScalaCompilerConfiguration.instanceIn(e.getProject).defaultProfile.getSettings
      }
      settings.experimental || settings.additionalCompilerOptions.contains("-Xexperimental")
    case _ => true //if there's no module e.scalaLanguageLevel is None, we treat it as Scala 2.12
  }

  /**
    * Determines if expected can be created with a Single Abstract Method and if so return the required ScType for it
    *
    * @see SCL-6140
    * @see https://github.com/scala/scala/pull/3018/
    */
  def toSAMType(expected: ScType, scalaScope: GlobalSearchScope, languageLevel: ScalaLanguageLevel)
               (implicit typeSystem: TypeSystem): Option[ScType] = {
    def constructorValidForSAM(constructor: PsiMethod): Boolean = {
      val isPublicAndParameterless = constructor.getModifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
        constructor.getParameterList.getParametersCount == 0
      constructor match {
        case scalaConstr: ScPrimaryConstructor if isPublicAndParameterless => scalaConstr.effectiveParameterClauses.size < 2
        case _ => isPublicAndParameterless
      }
    }

    expected.extractClassType() match {
      case Some((cl, sub)) =>
        cl match {
          case templDef: ScTemplateDefinition => //it's a Scala class or trait
            def selfTypeValid: Boolean = {
              templDef.selfType match {
                case Some(selfParam: ScParameterizedType) => templDef.getType(TypingContext.empty) match {
                  case Success(classParamTp: ScParameterizedType, _) => selfParam.designator.conforms(classParamTp.designator)
                  case _ => false
                }
                case Some(selfTp) => templDef.getType(TypingContext.empty) match {
                  case Success(classType, _) => selfTp.conforms(classType)
                  case _ => false
                }
                case _ => true
              }
            }
            val abst = templDef.allSignatures.filter(TypeDefinitionMembers.ParameterlessNodes.isAbstract)
            abst match {
              case (Seq(PhysicalSignature(fun: ScFunction, _))) =>
                val isScala211 = languageLevel == ScalaLanguageLevel.Scala_2_11
                def constructorValid = templDef match {
                  case cla: ScClass => cla.constructor.fold(false)(constructorValidForSAM)
                  case _: ScTrait => true
                  case _ => false
                }

                def hasOneParamClause = fun.paramClauses.clauses.length == 1

                def selfTypeCorrectIfScala212 = isScala211 || selfTypeValid

                if (constructorValid && hasOneParamClause && !fun.hasTypeParameters && selfTypeCorrectIfScala212) {
                  fun.getType() match {
                    case Success(tp, _) =>
                      val subbed = sub.subst(tp)
                      val extrapolated = extrapolateWildcardBounds(subbed, expected, fun.getProject, scalaScope, languageLevel)
                      extrapolated.orElse(Some(subbed))
                    case _ => None
                  }
                } else None
              case _ =>  None
            }
          case _ => //it's a Java abstract class or interface
            def overridesConcreteMethod(method: PsiMethod): Boolean = {
              method.findSuperMethods().exists(!_.hasAbstractModifier)
            }

            val abst: Array[PsiMethod] = cl.getMethods.filter {
              case method if method.hasAbstractModifier => true
              case _ => false
            } match {
              case array if array.length > 0 => array.filterNot(overridesConcreteMethod)
              case any => any
            }
            val constructors: Array[PsiMethod] = cl.getConstructors
            val constructorValid = constructors.exists(constructorValidForSAM) || constructors.isEmpty
            //must have exactly one abstract member and SAM must be monomorphic
            val valid = constructorValid && abst.length == 1 && !abst.head.hasTypeParameters
            if (valid) {
              //need to generate ScType for Java method
              val method = abst.head
              val project = method.getProject
              val returnType: ScType = method.getReturnType.toScType()
              val params: Array[ScType] = method.getParameterList.getParameters.map {
                param: PsiParameter => param.getTypeElement.getType.toScType()
              }
              val fun = FunctionType(returnType, params)(project, scalaScope)
              val subbed = sub.subst(fun)
              extrapolateWildcardBounds(subbed, expected, project, scalaScope, languageLevel) match {
                case s@Some(_) => s
                case _ => Some(subbed)
              }
            } else None
        }
      case None => None
    }
  }

  /**
   * In some cases existential bounds can be simplified without losing precision
   *
   * trait Comparinator[T] { def compare(a: T, b: T): Int }
   *
   * trait Test {
   *   def foo(a: Comparinator[_ >: String]): Int
   * }
   *
   * can be simplified to:
   *
   * trait Test {
   *   def foo(a: Comparinator[String]): Int
   * }
   *
   * @see https://github.com/scala/scala/pull/4101
   * @see SCL-8956
   */
  private def extrapolateWildcardBounds(tp: ScType, expected: ScType, proj: Project, scope: GlobalSearchScope, scalaVersion: ScalaLanguageLevel)
                                       (implicit typeSystem: TypeSystem = proj.typeSystem): Option[ScType] = {
    expected match {
      case ScExistentialType(ParameterizedType(_, _), wildcards) =>
        tp match {
          case FunctionType(retTp, params) =>
            def convertParameter(tpArg: ScType, variance: Int): ScType = {
              tpArg match {
                case ParameterizedType(des, tpArgs) => ScParameterizedType(des, tpArgs.map(convertParameter(_, variance)))
                case ScExistentialType(param: ScParameterizedType, _) if scalaVersion == ScalaLanguageLevel.Scala_2_11 =>
                  convertParameter(param, variance)
                case _ =>
                  wildcards.find(_.name == tpArg.canonicalText) match {
                    case Some(wildcard) =>
                      (wildcard.lower, wildcard.upper) match {
                        case (lo, Any) if variance == ScTypeParam.Contravariant => lo
                        case (Nothing, hi) if variance == ScTypeParam.Covariant => hi
                        case _ => tpArg
                      }
                    case _ => tpArg
                  }
              }
            }
            //parameter clauses are contravariant positions, return types are covariant positions
            val newParams = params.map(convertParameter(_, ScTypeParam.Contravariant))
            val newRetTp = convertParameter(retTp, ScTypeParam.Covariant)
            Some(FunctionType(newRetTp, newParams)(proj, scope))
          case _ => None
        }
      case _ => None
    }
  }

  def isImplicit(namedElement: PsiNamedElement): Boolean = {
    namedElement match {
      case s: ScModifierListOwner => s.hasModifierProperty("implicit")
      case named: ScNamedElement =>
        ScalaPsiUtil.nameContext(named) match {
          case s: ScModifierListOwner => s.hasModifierProperty("implicit")
          case _ => false
        }
      case _ => false
    }
  }

  def replaceBracesWithParentheses(element: ScalaPsiElement): Unit = {
    val manager = element.getManager
    val block = ScalaPsiElementFactory.parseElement("(_)", manager)

    for (lBrace <- Option(element.findFirstChildByType(ScalaTokenTypes.tLBRACE))) {
      lBrace.replace(block.getFirstChild)
    }

    for (rBrace <- Option(element.findFirstChildByType(ScalaTokenTypes.tRBRACE))) {
      rBrace.replace(block.getLastChild)
    }
  }
}
