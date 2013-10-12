package org.jetbrains.plugins.scala
package lang
package psi

import api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScRefinement, ScExistentialClause, ScTypeElement}
import api.toplevel.imports.usages.ImportUsed
import api.toplevel.imports.{ScImportStmt, ScImportExpr, ScImportSelector, ScImportSelectors}
import api.toplevel.packaging.{ScPackaging, ScPackageContainer}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile, ScalaRecursiveElementVisitor}
import com.intellij.psi.scope.PsiScopeProcessor
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef._
import impl.expr.ScBlockExprImpl
import impl.ScalaPsiManager.ClassCategory
import impl.toplevel.typedef.TypeDefinitionMembers
import impl.{ScalaPsiManager, ScalaPsiElementFactory}
import implicits.ScImplicitlyConvertible
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import api.expr.xml.ScXmlExpr
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types._
import api.statements._
import com.intellij.psi._
import codeStyle.CodeStyleSettingsManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPatternArgumentList, ScBindingPattern, ScReferencePattern, ScCaseClause}
import stubs.ScModifiersStub
import types.Compatibility.Expression
import params._
import parser.parsing.expressions.InfixExpr
import parser.util.ParserUtils
import result.{TypingContext, TypeResult}
import structureView.ScalaElementPresentation
import com.intellij.psi.util._
import formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.roots.{ProjectRootManager, ProjectFileIndex}
import lang.resolve.processor._
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import com.intellij.psi.impl.light.LightModifierList
import scala.collection.immutable.Stream
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.openapi.module.{ModuleUtilCore, Module}
import config.ScalaFacet
import scala.reflect.NameTransformer
import caches.CachesUtil
import extensions._
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import com.intellij.psi.impl.compiled.ClsFileImpl
import scala.collection.{mutable, Set, Seq}
import org.apache.log4j.Level
import com.intellij.openapi.diagnostic
import scala.util.control.ControlThrowable
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.java.JavaLanguage
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import com.intellij.openapi.util.TextRange
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.types.ScUndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType
import org.jetbrains.plugins.scala.lang.psi.types.ScAbstractType
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.resolve.processor.MostSpecificUtil
import org.jetbrains.plugins.scala.lang.psi.types.Conformance.AliasType
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.ScTupleType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.JavaArrayType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScProjectionType

/**
 * User: Alexander Podkhalyuzin
 */
object ScalaPsiUtil {
  @tailrec
  def firstLeaf(elem: PsiElement): PsiElement = {
    val firstChild: PsiElement = elem.getFirstChild
    if (firstChild == null) return elem
    firstLeaf(firstChild)
  }

  def isBooleanBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    s.annotations.exists {
      case annot =>
        annot.annotationExpr.constr.args
    }
    if (noResolve) {
      s.annotations.exists {
        case annot => Set("scala.reflect.BooleanBeanProperty", "reflect.BooleanBeanProperty",
          "BooleanBeanProperty", "scala.beans.BooleanBeanProperty", "beans.BooleanBeanProperty").
                contains(annot.typeElement.getText.replace(" ", ""))
      }
    } else {
      s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None ||
        s.hasAnnotation("scala.beans.BooleanBeanProperty") != None
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
      s.hasAnnotation("scala.reflect.BeanProperty") != None ||
        s.hasAnnotation("scala.beans.BeanProperty") != None
    }
  }

  def getDependentItem(element : PsiElement,
                       dep_item: Object = PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT): Option[Object] = {
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
        if (!ProjectRootManager.getInstance(element.getProject).getFileIndex.isInContent(file.getVirtualFile)) {
          return Some(dep_item)
        }
        var dir = file.getParent
        while (dir != null) {
          if (dir.getName == "scala-library.jar") return None
          dir = dir.getParent
        }
        Some(ProjectRootManager.getInstance(element.getProject))
      case cls: ClsFileImpl => Some(ProjectRootManager.getInstance(element.getProject))
      case _ => Some(dep_item)
    }
  }

  @tailrec
  def withEtaExpansion(expr: ScExpression): Boolean = {
    expr.getContext match {
      case call: ScMethodCall => false
      case g: ScGenericCall => withEtaExpansion(g)
      case p: ScParenthesisedExpr => withEtaExpansion(p)
      case _ => true
    }
  }

  @tailrec
  def isLocalClass(td: PsiClass): Boolean = {
    td.getParent match {
      case tb: ScTemplateBody =>
        val parent = PsiTreeUtil.getParentOfType(td, classOf[PsiClass], true)
        if (parent == null) true
        if (parent.isInstanceOf[ScNewTemplateDefinition]) return true
        isLocalClass(parent)
      case _: ScPackaging | _: ScalaFile => false
      case _ => true
    }
  }

  def convertMemberName(s: String): String = {
    if (s == null || s.isEmpty) return s
    val s1 = if (s(0) == '`' && s.length() > 1) s.drop(1).dropRight(1) else s
    NameTransformer.decode(s1)
  }

  def memberNamesEquals(l: String, r: String): Boolean = {
    if (l == r) return true
    convertMemberName(l) == convertMemberName(r)
  }

  def cachedDeepIsInheritor(clazz: PsiClass, base: PsiClass): Boolean = {
    val manager = ScalaPsiManager.instance(clazz.getProject)
    manager.cachedDeepIsInheritor(clazz, base)
  }

  def hasScalaFacet(element: PsiElement): Boolean = {
    val module: Module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module == null) false
    else ScalaFacet.findIn(module) != None
  }

  def lastChildElementOrStub(element: PsiElement): PsiElement = {
    element match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null => {
        val stub = st.getStub
        val children = stub.getChildrenStubs
        if (children.size() == 0) element.getLastChild
        else children.get(children.size() - 1).getPsi
      }
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
        val unitExpr = ScalaPsiElementFactory.createExpressionFromText("()", manager)
        Some(Seq(Expression(unitExpr)))
      case _ =>
        val exprTypes: Seq[ScType] =
          s.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)).map {
            case (res, _) => res.getOrAny
          }
        val qual = "scala.Tuple" + exprTypes.length
        val tupleClass = ScalaPsiManager.instance(manager.getProject).getCachedClass(scope, qual)
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
      case _: ScImportStmt => {
        typeResult match {
          case Success(t, _) => {
            (processor, place) match {
              case (b: BaseProcessor, p: ScalaPsiElement) => b.processType(subst subst t, p, state)
              case _ => true
            }
          }
          case _ => true
        }
      }
      case _ => true
    }
  }

  /**
  Pick all type parameters by method maps them to the appropriate type arguments, if they are
   */
  def inferMethodTypesArgs(fun: PsiMethod, classSubst: ScSubstitutor): ScSubstitutor = {

    fun match {
      case fun: ScFunction =>
        fun.typeParameters.foldLeft(ScSubstitutor.empty) {
          (subst, tp) => subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)),
            new ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst), 1))
        }
      case fun: PsiMethod =>
        fun.getTypeParameters.foldLeft(ScSubstitutor.empty) {
          (subst, tp) => subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)),
            new ScUndefinedType(new ScTypeParameterType(tp: PsiTypeParameter, classSubst), 1))
        }
    }
  }

  def findImplicitConversion(e: ScExpression, refName: String, ref: PsiElement, processor: BaseProcessor,
                             noImplicitsForArgs: Boolean):
    Option[ImplicitResolveResult] = {
    //TODO! remove this after find a way to improve implicits according to compiler.
    val isHardCoded = refName == "+" &&
      e.getTypeWithoutImplicits(TypingContext.empty).map(_.isInstanceOf[ValType]).getOrElse(false)
    val kinds = processor.kinds
    var implicitMap: Seq[ImplicitResolveResult] = Seq.empty
    def checkImplicits(secondPart: Boolean, noApplicability: Boolean, noImplicitsForArgs: Boolean = noImplicitsForArgs) {
      lazy val args = processor match {
        case m: MethodResolveProcessor => m.argumentClauses.flatMap(_.map(
          _.getTypeAfterImplicitConversion(checkImplicits = false, isShape = m.isShapeResolve, None)._1.getOrAny
        ))
        case _ => Seq.empty
      }
      val convertible: ScImplicitlyConvertible = new ScImplicitlyConvertible(e)
      val mp =
        if (noApplicability) convertible.implicitMap(args = args)
        else if (!secondPart) convertible.implicitMapFirstPart()
        else convertible.implicitMapSecondPart(args = args)
      implicitMap = mp.flatMap({
        case implRes: ImplicitResolveResult => {
          ProgressManager.checkCanceled()
          if (!isHardCoded || !implRes.tp.isInstanceOf[ValType]) {
            val newProc = new ResolveProcessor(kinds, ref, refName)
            newProc.processType(implRes.tp, e, ResolveState.initial)
            val res = !newProc.candidatesS.isEmpty
            if (!noApplicability && res && processor.isInstanceOf[MethodResolveProcessor]) {
              val mrp = processor.asInstanceOf[MethodResolveProcessor]
              val newProc = new MethodResolveProcessor(ref, refName, mrp.argumentClauses, mrp.typeArgElements,
                implRes.element match {
                  case fun: ScFunction if fun.hasTypeParameters => fun.typeParameters.map(tp => TypeParameter(tp.name, types.Nothing, types.Any, tp))
                  case _ => Seq.empty
                }, kinds,
                mrp.expectedOption, mrp.isUnderscore, mrp.isShapeResolve, mrp.constructorResolve, noImplicitsForArgs = noImplicitsForArgs)
              val tp = implRes.tp
              newProc.processType(tp, e, ResolveState.initial)
              val candidates = newProc.candidatesS.filter(_.isApplicable)
              if (candidates.isEmpty) Seq.empty
              else {
                implRes.element match {
                  case fun: ScFunction if fun.hasTypeParameters =>
                    val rr = candidates.iterator.next()
                    rr.resultUndef match {
                      case Some(undef) =>
                        undef.getSubstitutor match {
                          case Some(subst) =>
                            Seq(ImplicitResolveResult(subst.subst(implRes.getTypeWithDependentSubstitutor), fun,
                              implRes.importUsed, implRes.subst, implRes.implicitDependentSubst, implRes.isFromCompanion))
                          case _ => Seq(implRes)
                        }
                      case _ => Seq(implRes)
                    }
                  case _ => Seq(implRes)
                }
              }
            } else {
              if (res) Seq(implRes)
              else Seq.empty
            }
          } else Seq.empty
        }
      })
    }
    //todo: insane logic. Important to have to navigate to problematic method, in case of failed resolve. That's why we need to have noApplicability parameter
    checkImplicits(secondPart = false, noApplicability = false)
    if (implicitMap.size > 1) {
      val oldMap = implicitMap
      checkImplicits(secondPart = false, noApplicability = false, noImplicitsForArgs = true)
      if (implicitMap.isEmpty) implicitMap = oldMap
    } else if (implicitMap.isEmpty) {
      checkImplicits(secondPart = true, noApplicability = false)
      if (implicitMap.size > 1) {
        val oldMap = implicitMap
        checkImplicits(secondPart = false, noApplicability = false, noImplicitsForArgs = true)
        if (implicitMap.isEmpty) implicitMap = oldMap
      } else if (implicitMap.isEmpty) checkImplicits(secondPart = false, noApplicability = true)
    }
    if (implicitMap.isEmpty) None
    else if (implicitMap.length == 1) Some(implicitMap.apply(0))
    else MostSpecificUtil(ref, 1).mostSpecificForImplicit(implicitMap.toSet)
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

  def processTypeForUpdateOrApplyCandidates(call: MethodInvocation, tp: ScType, isShape: Boolean,
                                            noImplicits: Boolean): Array[ScalaResolveResult] = {
    val isUpdate = call.isUpdateCall
    val methodName = if (isUpdate) "update" else "apply"
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
          case tp: ScTypePolymorphicType => //that means that generic call is important here
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
    val processor = new MethodResolveProcessor(expr, methodName, args :: Nil, typeArgs, typeParams,
      isShapeResolve = isShape, enableTupling = true)
    var candidates: Set[ScalaResolveResult] = Set.empty
    exprTp match {
      case ScTypePolymorphicType(internal, typeParam) if !typeParam.isEmpty &&
        !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[ScUndefinedType] =>
        processor.processType(internal, call.getEffectiveInvokedExpr, ResolveState.initial())
        candidates = processor.candidatesS
      case _ =>
    }
    if (candidates.isEmpty) {
      processor.processType(exprTp.inferValueType, call.getEffectiveInvokedExpr, ResolveState.initial)
      candidates = processor.candidatesS
    }

    if (!noImplicits && candidates.forall(!_.isApplicable)) {
      //should think about implicit conversions
      findImplicitConversion(expr, methodName, call, processor, noImplicitsForArgs = false) match {
        case Some(res) =>
          ProgressManager.checkCanceled()
          val function = res.element
          var state = ResolveState.initial.put(ImportUsed.key, res.importUsed).
            put(CachesUtil.IMPLICIT_FUNCTION, function)
          res.getClazz match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          processor.processType(res.getTypeWithDependentSubstitutor, expr, state)
        case _ =>
      }
      candidates = processor.candidatesS
    }
    candidates.toArray
  }

  def processTypeForUpdateOrApply(tp: ScType, call: MethodInvocation,
                                  isShape: Boolean): Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[PsiElement])] = {

    def checkCandidates(withImplicits: Boolean): Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[PsiElement])] = {
      val candidates: Array[ScalaResolveResult] = processTypeForUpdateOrApplyCandidates(call, tp, isShape, noImplicits = !withImplicits)
      PartialFunction.condOpt(candidates) {
        case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
          val res = fun match {
            case fun: ScFun => (s.subst(fun.polymorphicType), r.importsUsed, r.implicitFunction, Some(fun))
            case fun: ScFunction => (s.subst(fun.polymorphicType()), r.importsUsed, r.implicitFunction, Some(fun))
            case meth: PsiMethod => (ResolveUtils.javaPolymorphicType(meth, s, call.getResolveScope), r.importsUsed,
              r.implicitFunction, Some(fun))
          }
        call.getInvokedExpr.getNonValueType(TypingContext.empty) match {
          case Success(ScTypePolymorphicType(_, typeParams), _) =>
            res.copy(_1 = res._1 match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2))
              case _ =>
                ScTypePolymorphicType(res._1, typeParams)
            })
          case _ => res
        }
      }
    }

    checkCandidates(withImplicits = false).orElse(checkCandidates(withImplicits = true))
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
              case t: ScTypeParameterType =>
                if (typeParameters.exists {
                  case TypeParameter(_, _, _, ptp) if ptp == t.param && ptp.getOwner != ownerPtp.getOwner => true
                  case _ => false
                }) res = None
              case _ =>
            }
            (false, tp)
          }
          res
        }

        ScTypePolymorphicType(internal, typeParameters.map {
          case t@TypeParameter(name, lowerType, upperType, ptp) =>
            TypeParameter(name, hasBadLinks(lowerType, ptp).getOrElse(Nothing),
              hasBadLinks(upperType, ptp).getOrElse(Any), ptp)
        })
      case _ => tp
    }
  }

  @tailrec
  def isAnonymousExpression(expr: ScExpression): (Int, ScExpression) = {
    val seq = ScUnderScoreSectionUtil.underscores(expr)
    if (seq.length > 0) return (seq.length, expr)
    expr match {
      case b: ScBlockExpr =>
        if (b.statements != 1) (-1, expr) else if (b.lastExpr == None) (-1, expr) else isAnonymousExpression(b.lastExpr.get)
      case p: ScParenthesisedExpr => p.expr match {case Some(x) => isAnonymousExpression(x) case _ => (-1, expr)}
      case f: ScFunctionExpr =>  (f.parameters.length, expr)
      case _ => (-1, expr)
    }
  }

  def getModule(element: PsiElement): Module = {
    val index: ProjectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    index.getModuleForFile(element.getContainingFile.getVirtualFile)
  }

  def collectImplicitObjects(_tp: ScType, place: PsiElement): Seq[ScType] = {
    val tp = ScType.removeAliasDefinitions(_tp)
    val projectOpt = Option(place).map(_.getProject)
    val parts: ListBuffer[ScType] = new ListBuffer[ScType]
    val visited = new mutable.HashSet[ScType]()
    def collectParts(tp: ScType, place: PsiElement) {
      if (visited.contains(tp)) return
      visited += tp
      tp.isAliasType match {
        case Some(AliasType(_, _, upper)) =>
          upper.foreach(collectParts(_, place))
        case _ =>
      }
      tp match {
        case ScDesignatorType(v: ScBindingPattern) => v.getType(TypingContext.empty).foreach(collectParts(_, place))
        case ScDesignatorType(v: ScFieldId) => v.getType(TypingContext.empty).foreach(collectParts(_, place))
        case ScDesignatorType(p: ScParameter) => p.getType(TypingContext.empty).foreach(collectParts(_, place))
        case ScCompoundType(comps, _, _, _) => comps.foreach(collectParts(_, place))
        case p@ScParameterizedType(a: ScAbstractType, args) =>
          collectParts(a, place)
          args.foreach(collectParts(_, place))
        case p@ScParameterizedType(des, args) =>
          ScType.extractClass(p, projectOpt) match {
            case Some(pair) => parts += des
            case _ =>
          }
          collectParts(des, place)
          args.foreach(collectParts(_, place))
        case j: JavaArrayType =>
          val parameterizedType = j.getParameterizedType(place.getProject, place.getResolveScope)
          collectParts(parameterizedType.getOrElse(return), place)
        case f@ScFunctionType(retType, params) =>
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
          params.foreach(collectParts(_, place))
          collectParts(retType, place)
        case f@ScTupleType(params) =>
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
          params.foreach(collectParts(_, place))
        case proj@ScProjectionType(projected, _, _) =>
          collectParts(projected, place)
          proj.actualElement match {
            case v: ScBindingPattern => v.getType(TypingContext.empty).map(proj.actualSubst.subst(_)).foreach(collectParts(_, place))
            case v: ScFieldId => v.getType(TypingContext.empty).map(proj.actualSubst.subst(_)).foreach(collectParts(_, place))
            case v: ScParameter => v.getType(TypingContext.empty).map(proj.actualSubst.subst(_)).foreach(collectParts(_, place))
            case _ =>
          }
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
        case ScAbstractType(_, lower, upper) =>
          collectParts(lower, place)
          collectParts(upper, place)
        case ScExistentialType(quant, _) =>
          collectParts(quant, place)
        case _=> {
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) =>
              val packObjects = pair.contexts.flatMap {
                case x: ScPackageLike =>
                  x.findPackageObject(place.getResolveScope).toIterator
                case _ => Iterator()
              }
              parts += tp
              packObjects.foreach(p => parts += ScDesignatorType(p))
            case _ =>
          }
        }
      }
    }
    collectParts(tp, place)
    val res: mutable.HashSet[ScType] = new mutable.HashSet
    for (part <- parts) {
      //here we want to convert projection types to right projections
      val visited = new mutable.HashSet[PsiClass]()
      def collectObjects(tp: ScType) {
        tp match {
          case types.Any => return
          case tp: StdType if Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char").contains(tp.name) =>
            val obj = ScalaPsiManager.instance(place.getProject).
              getCachedClass("scala." + tp.name, place.getResolveScope, ClassCategory.OBJECT)
            obj match {
              case o: ScObject => res += ScDesignatorType(o)
              case _ =>
            }
            return
          case ScDesignatorType(ta: ScTypeAliasDefinition) =>
            collectObjects(ta.aliasedType.getOrAny)
            return
          case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
            collectObjects(p.actualSubst.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny))
            return
          case ScParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) => {
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))), args)
            collectObjects(genericSubst.subst(ta.aliasedType.getOrAny))
            return
          }
          case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] => {
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(p.actualElement.asInstanceOf[ScTypeAliasDefinition].typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp)
              )), args)
            val s = p.actualSubst.followed(genericSubst)
            collectObjects(s.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny))
            return
          }
          case _ =>
        }
        ScType.extractClassType(tp, projectOpt) match {
          case Some((clazz: PsiClass, subst: ScSubstitutor)) if !visited.contains(clazz) =>
            clazz match {
              case o: ScObject => res += tp
              case _ =>
                getCompanionModule(clazz) match {
                  case Some(obj: ScObject) =>
                    tp match {
                      case ScProjectionType(proj, _, s) =>
                        res += ScProjectionType(proj, obj, s)
                      case ScParameterizedType(ScProjectionType(proj, _, s), _) =>
                        res += ScProjectionType(proj, obj, s)
                      case _ =>
                        res += ScDesignatorType(obj)
                    }
                  case _ =>
                }
            }
            clazz match {
              case td: ScTemplateDefinition =>
                td.superTypes.foreach((tp: ScType) => {
                  visited += clazz
                  collectObjects(subst.subst(tp))
                  visited -= clazz
                })
              case clazz: PsiClass =>
                clazz.getSuperTypes.foreach(tp => {
                  val stp = ScType.create(tp, place.getProject, place.getResolveScope)
                  visited += clazz
                  collectObjects(subst.subst(stp))
                  visited -= clazz
                })
            }
          case _ =>
        }
      }
      collectObjects(part)
    }
    res.toSeq
  }

  def getSingletonStream[A](elem: => A): Stream[A] = {
    new Stream[A] {
      override def head: A = elem

      override def tail: Stream[A] = Stream.empty

      protected def tailDefined: Boolean = false

      override def isEmpty: Boolean = false
    }
  }
  def getTypesStream(elems: Seq[PsiParameter]): Stream[ScType] = {
    getTypesStream(elems, (param: PsiParameter) => {
      param match {
        case scp: ScParameter => scp.getType(TypingContext.empty).getOrNothing
        case p =>
          val treatJavaObjectAsAny = p.parents.findByType(classOf[PsiClass]) match {
            case Some(cls) if cls.qualifiedName == "java.lang.Object" => true // See SCL-3036
            case _ => false
          }
          p.exactParamType(treatJavaObjectAsAny)
      }
    })
  }

  def getTypesStream[T](elems: Seq[T], fun: T => ScType): Stream[ScType] = {
    // Do not consider elems.toStream.map { case x: ScType => ...; case y => }
    // Reason is performance: first element will not be lazy in any case.
    if (elems.isEmpty) return Stream.empty
    new Stream[ScType] {
      private var h: ScType = null

      override def head: ScType = {
        if (h == null)
          h = fun(elems.head)
        h
      }

      override def isEmpty: Boolean = false

      private[this] var tlVal: Stream[ScType] = null
      def tailDefined = tlVal ne null

      override def tail: Stream[ScType] = {
        if (!tailDefined) tlVal = getTypesStream(elems.tail, fun)
        tlVal
      }
    }
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
        expr match {
          case f: ScForStatement => {
            f.getDesugarizedExpr match {
              case Some(e) => res = res ++ getExprImports(e)
              case _ =>
            }
          }
          case ref: ScReferenceExpression => {
            for (rr <- ref.multiResolve(false) if rr.isInstanceOf[ScalaResolveResult]) {
              res = res ++ rr.asInstanceOf[ScalaResolveResult].importsUsed
            }
            super.visitExpression(expr)
          }
          case _ => {
            super.visitExpression(expr)
          }
        }
      }
    }
    z.accept(visitor)
    res
  }

  def getPsiElementId(elem: PsiElement): String = {
    if (elem == null) return "NullElement"
    try {
      elem match {
        case tp: ScTypeParam => tp.getPsiElementId
        case p: PsiTypeParameter => " in: Java" //Two parameters from Java can't be used with same name in same place
        case _ => {
          val containingFile: PsiFile = elem.getContainingFile
          " in:" + (if (containingFile != null) containingFile.name else "NoFile") + ":" +
            (if (elem.getTextRange != null) elem.getTextRange.getStartOffset else "NoRange")
        }
      }
    }
    catch {
      case pieae: PsiInvalidElementAccessException => "NotValidElement"
    }
  }

  def getSettings(project: Project): ScalaCodeStyleSettings = {
    CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
  }

  def undefineSubstitutor(typeParams: Seq[TypeParameter]): ScSubstitutor = {
    typeParams.foldLeft(ScSubstitutor.empty) {
      (subst: ScSubstitutor, tp: TypeParameter) =>
        subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
          new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
    }
  }
  def localTypeInference(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                         typeParams: Seq[TypeParameter],
                         shouldUndefineParameters: Boolean = true,
                         safeCheck: Boolean = false,
                         filterTypeParams: Boolean = true,
                         checkAnyway: Boolean = false): ScTypePolymorphicType = {
    localTypeInferenceWithApplicability(retType, params, exprs, typeParams, shouldUndefineParameters, safeCheck,
      filterTypeParams, checkAnyway)._1
  }


  class SafeCheckException extends ControlThrowable

  //todo: move to InferUtil
  def localTypeInferenceWithApplicability(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                          typeParams: Seq[TypeParameter],
                                          shouldUndefineParameters: Boolean = true,
                                          safeCheck: Boolean = false,
                                          filterTypeParams: Boolean = true,
                                          checkAnyway: Boolean = false): (ScTypePolymorphicType, Seq[ApplicabilityProblem]) = {
    val (tp, problems, _, _) = localTypeInferenceWithApplicabilityExt(retType, params, exprs, typeParams,
      shouldUndefineParameters, safeCheck, filterTypeParams, checkAnyway)
    (tp, problems)
  }

  def localTypeInferenceWithApplicabilityExt(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                             typeParams: Seq[TypeParameter],
                                             shouldUndefineParameters: Boolean = true,
                                             safeCheck: Boolean = false,
                                             filterTypeParams: Boolean = true,
                                             checkAnyway: Boolean = false
    ): (ScTypePolymorphicType, Seq[ApplicabilityProblem], Seq[(Parameter, ScExpression)], Seq[(Parameter, ScType)]) = {
    // See SCL-3052, SCL-3058
    // This corresponds to use of `isCompatible` in `Infer#methTypeArgs` in scalac, where `isCompatible` uses `weak_<:<`
    val s: ScSubstitutor = if (shouldUndefineParameters) undefineSubstitutor(typeParams) else ScSubstitutor.empty
    val abstractSubst = ScTypePolymorphicType(retType, typeParams).abstractTypeSubstitutor
    val paramsWithUndefTypes = params.map(p => p.copy(paramType = s.subst(p.paramType),
      expectedType = abstractSubst.subst(p.paramType)))
    val c = Compatibility.checkConformanceExt(checkNames = true, paramsWithUndefTypes, exprs, checkWithImplicits = true, isShapesResolve = false)
    val tpe = if (c.problems.isEmpty) {
      var un: ScUndefinedSubstitutor = c.undefSubst
      val subst = c.undefSubst
      subst.getSubstitutor(!safeCheck) match {
        case Some(unSubst) =>
          if (!filterTypeParams) {
            val undefiningSubstitutor = new ScSubstitutor(typeParams.map(typeParam => {
              ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)), new ScUndefinedType(new ScTypeParameterType(typeParam.ptp, ScSubstitutor.empty)))
            }).toMap, Map.empty, None)
            ScTypePolymorphicType(retType, typeParams.map(tp => {
              var lower = tp.lowerType
              var upper = tp.upperType
              def hasRecursiveTypeParameters(typez: ScType): Boolean = {
                var hasRecursiveTypeParameters = false
                typez.recursiveUpdate {
                  case tpt: ScTypeParameterType =>
                    typeParams.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)) ==(tpt.name, tpt.getId)) match {
                      case None => (true, tpt)
                      case _ =>
                        hasRecursiveTypeParameters = true
                        (true, tpt)
                    }
                  case tp: ScType => (hasRecursiveTypeParameters, tp)
                }
                hasRecursiveTypeParameters
              }
              subst.lMap.get((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))) match {
                case Some(addLower) =>
                  val substedLowerType = unSubst.subst(lower)
                  if (hasRecursiveTypeParameters(substedLowerType)) lower = addLower
                  else lower = Bounds.lub(substedLowerType, addLower)
                case None =>
                  lower = unSubst.subst(lower)
              }
              subst.rMap.get((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))) match {
                case Some(addUpper) =>
                  val substedUpperType = unSubst.subst(upper)
                  if (hasRecursiveTypeParameters(substedUpperType)) upper = addUpper
                  else upper = Bounds.glb(substedUpperType, addUpper)
                case None =>
                  upper = unSubst.subst(upper)
              }

              if (safeCheck && !undefiningSubstitutor.subst(lower).conforms(undefiningSubstitutor.subst(upper), checkWeak = true))
                throw new SafeCheckException
              TypeParameter(tp.name, lower, upper, tp.ptp)
            }))
          } else {
            typeParams.foreach {case tp =>
              val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
              if (un.names.contains(name)) {
                def hasRecursiveTypeParameters(typez: ScType): Boolean = {
                  var hasRecursiveTypeParameters = false
                  typez.recursiveUpdate {
                    case tpt: ScTypeParameterType =>
                      typeParams.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)) ==(tpt.name, tpt.getId)) match {
                        case None => (true, tpt)
                        case _ =>
                          hasRecursiveTypeParameters = true
                          (true, tpt)
                      }
                    case tp: ScType => (hasRecursiveTypeParameters, tp)
                  }
                  hasRecursiveTypeParameters
                }
                //todo: add only one of them according to variance
                if (tp.lowerType != Nothing) {
                  val substedLowerType = unSubst.subst(tp.lowerType)
                  if (!hasRecursiveTypeParameters(substedLowerType)) {
                    un = un.addLower(name, substedLowerType, additional = true)
                  }
                }
                if (tp.upperType != Any) {
                  val substedUpperType = unSubst.subst(tp.upperType)
                  if (!hasRecursiveTypeParameters(substedUpperType)) {
                    un = un.addUpper(name, substedUpperType, additional = true)
                  }
                }
              }
            }
            un.getSubstitutor match {
              case Some(unSubstitutor) =>
                ScTypePolymorphicType(unSubstitutor.subst(retType), typeParams.filter {case tp =>
                  val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
                  !un.names.contains(name)
                }.map(tp => TypeParameter(tp.name, unSubstitutor.subst(tp.lowerType), unSubstitutor.subst(tp.upperType), tp.ptp)))
              case _ =>
                if (checkAnyway) throw new SafeCheckException
                ScTypePolymorphicType(unSubst.subst(retType), typeParams.filter {case tp =>
                  val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
                  !un.names.contains(name)
                }.map(tp => TypeParameter(tp.name, unSubst.subst(tp.lowerType), unSubst.subst(tp.upperType), tp.ptp)))
            }
          }
        case None => throw new SafeCheckException
      }
    } else ScTypePolymorphicType(retType, typeParams)
    (tpe, c.problems, c.matchedArgs, c.matchedTypes)
  }

  def getElementsRange(start: PsiElement, end: PsiElement): Seq[PsiElement] = {
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
    buffer.toSeq
  }

  def getParents(elem: PsiElement, topLevel: PsiElement): List[PsiElement] = {
    @tailrec
    def inner(parent: PsiElement, k: List[PsiElement] => List[PsiElement]): List[PsiElement] = {
      if (parent != topLevel && parent != null)
        inner(parent.getParent, {l => parent :: k(l)})
      else k(Nil)
    }
    inner(elem, (l: List[PsiElement]) => l)
  }

  //todo: always returns true?!
  @tailrec
  def shouldCreateStub(elem: PsiElement): Boolean = {
    return true
    elem match {
      case _: ScAnnotation | _: ScAnnotations | _: ScFunction | _: ScTypeAlias | _: ScAccessModifier |
              _: ScModifierList | _: ScVariable | _: ScValue | _: ScParameter | _: ScParameterClause |
              _: ScParameters | _: ScTypeParamClause | _: ScTypeParam | _: ScImportExpr |
              _: ScImportSelector | _: ScImportSelectors | _: ScPatternList | _: ScReferencePattern => shouldCreateStub(elem.getParent)
      /*case _: ScalaFile | _: ScPrimaryConstructor | _: ScSelfTypeElement | _: ScEarlyDefinitions |
              _: ScPackageStatement | _: ScPackaging | _: ScTemplateParents | _: ScTemplateBody |
              _: ScExtendsBlock | _: ScTypeDefinition => return true*/
      case _ => true
    }
  }
  
  def getFirstStubOrPsiElement(elem: PsiElement): PsiElement = {
    elem match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null => {
        val stub = st.getStub
        val childrenStubs = stub.getChildrenStubs
        if (childrenStubs.size() > 0) childrenStubs.get(0).getPsi
        else null
      }
      case file: PsiFileImpl if file.getStub != null => {
        val stub = file.getStub
        val childrenStubs = stub.getChildrenStubs
        if (childrenStubs.size() > 0) childrenStubs.get(0).getPsi
        else null
      }
      case _ => elem.getFirstChild
    }
  }

  def getPrevStubOrPsiElement(elem: PsiElement): PsiElement = {
    def workWithStub(stub: StubElement[_ <: PsiElement]): PsiElement = {
      val parent = stub.getParentStub
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
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null => {
        val stub = st.getStub
        workWithStub(stub)
      }
      case file: PsiFileImpl if file.getStub != null => {
        val stub = file.getStub
        workWithStub(stub)
      }
      case _ => elem.getPrevSibling
    }
  }

  def isLValue(elem: PsiElement) = elem match {
    case e: ScExpression => e.getParent match {
      case as: ScAssignStmt => as.getLExpression eq e
      case _ => false
    }
    case _ => false
  }

  def getNextStubOrPsiElement(elem: PsiElement): PsiElement = {
    elem match {
      case st: ScalaStubBasedElementImpl[_] if st.getStub != null => {
        val stub = st.getStub
        val parent = stub.getParentStub
        val children = parent.getChildrenStubs
        val index = children.indexOf(stub)
        if (index == -1) {
          elem.getNextSibling
        } else if (index >= children.size - 1) {
          null
        } else {
          children.get(index + 1).getPsi
        }
      }
      case _ => elem.getNextSibling
    }
  }

  def extractReturnType(tp: ScType): Option[ScType] = {
    tp match {
      case ScFunctionType(retType, _) => Some(retType)
      case ScParameterizedType(des, args) => {
        ScType.extractClass(des) match {
          case Some(clazz) if clazz.qualifiedName.startsWith("scala.Function") => Some(args(args.length - 1))
          case _ => None
        }
      }
      case _ => None
    }
  }

  def getPlaceTd(placer: PsiElement, ignoreTemplateParents: Boolean = false): ScTemplateDefinition = {
    val td = PsiTreeUtil.getContextOfType(placer, true, classOf[ScTemplateDefinition])
    if (ignoreTemplateParents) return td
    if (td == null) return null
    val res = td.extendsBlock.templateParents match {
      case Some(parents) => {
        if (PsiTreeUtil.isContextAncestor(parents, placer, true)) getPlaceTd(td)
        else td
      }
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

  def typesCallSubstitutor(tp: Seq[(String, String)], typeArgs: Seq[ScType]): ScSubstitutor = {
    val map = new collection.mutable.HashMap[(String, String), ScType]
    for (i <- 0 to math.min(tp.length, typeArgs.length) - 1) {
      map += ((tp(i), typeArgs(i)))
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
  }

  def genericCallSubstitutor(tp: Seq[(String, String)], typeArgs: Seq[ScTypeElement]): ScSubstitutor = {
    val map = new collection.mutable.HashMap[(String, String), ScType]
    for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
      map += ((tp(tp.length - 1 - i), typeArgs(typeArgs.length - 1 - i).getType(TypingContext.empty).getOrAny))
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
  }

  def genericCallSubstitutor(tp: Seq[(String, String)], gen: ScGenericCall): ScSubstitutor = {
    val typeArgs: Seq[ScTypeElement] = gen.arguments
    genericCallSubstitutor(tp, typeArgs)
  }

  def namedElementSig(x: PsiNamedElement): Signature =
    new Signature(x.name, Stream.empty, 0, ScSubstitutor.empty, Some(x))

  def superValsSignatures(x: PsiNamedElement, withSelfType: Boolean = false): Seq[Signature] = {
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
      case Some(node) => node.supers.map {_.info}
      case None =>
        throw new RuntimeException(s"internal error: could not find val matching: \n${x.getText}\n\nin class: \n${clazz.getText}")
    }


    val beanMethods = typed.getBeanMethods
    beanMethods.foreach {method =>
      val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)._1
      (sigs.get(new PhysicalSignature(method, ScSubstitutor.empty)): @unchecked) match {
        //partial match
        case Some(node) => res ++= node.supers.map {_.info}
        case None =>
      }
    }

    res

  }

  def superTypeMembers(element: PsiNamedElement, withSelfType: Boolean = false): Seq[PsiNamedElement] = {
    val empty = Seq.empty
    val clazz: ScTemplateDefinition = nameContext(element) match {
      case e @ (_: ScTypeAlias | _: ScTrait | _: ScClass) if e.getParent.isInstanceOf[ScTemplateBody] => e.asInstanceOf[ScMember].containingClass
      case _ => return empty
    }
    if (clazz == null) return empty
    val types = if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(clazz) else TypeDefinitionMembers.getTypes(clazz)
    val sigs = types.forName(element.name)._1
    val t = (sigs.get(element): @unchecked) match {
      //partial match
      case Some(x) => x.supers.map {_.info}
      case None =>
        throw new RuntimeException("internal error: could not find type matching: \n%s\n\nin class: \n%s".format(
        element.getText, clazz.getText
        ))
    }
    t
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

  def getEmptyModifierList(manager: PsiManager): PsiModifierList =
    new LightModifierList(manager, ScalaFileType.SCALA_LANGUAGE)

  def adjustTypes(element: PsiElement) {
    def replaceStablePath(ref: ScReferenceElement, name: String, toBind: PsiElement): PsiElement = {
      val replaced = ref.replace(ScalaPsiElementFactory.createReferenceFromText(name, toBind.getManager))
      replaced.asInstanceOf[ScStableCodeReferenceElement].bindToElement(toBind)
    }
    if (element == null) return
    for (child <- element.getChildren) {
      child match {
        case stableRef: ScStableCodeReferenceElement =>
          var aliasedRef: Option[ScReferenceElement] = None
          stableRef.resolve() match {
            case resolved if {aliasedRef = ScalaPsiUtil.importAliasFor(resolved, stableRef); aliasedRef.isDefined} =>
              stableRef.replace(aliasedRef.get)
            case named: PsiNamedElement if hasStablePath(named) =>
              named match {
                case clazz: PsiClass => replaceStablePath(stableRef, clazz.name, clazz)
                case typeAlias: ScTypeAlias if typeAlias.containingClass != null &&
                        Seq("scala", "scala.Predef").contains(typeAlias.containingClass.qualifiedName) =>
                  replaceStablePath(stableRef, typeAlias.name, typeAlias)
                case binding: ScBindingPattern => replaceStablePath(stableRef, binding.name, binding)
                case _ => adjustTypes(child)
              }
            case _ => adjustTypes(child)
          }
        case _ => adjustTypes(child)
      }
    }
  }

  def getMethodPresentableText(method: PsiMethod, subst: ScSubstitutor = ScSubstitutor.empty): String = {
    method match {
      case method: ScFunction => {
        ScalaElementPresentation.getMethodPresentableText(method, short = false, subst)
      }
      case _ => {
        val PARAM_OPTIONS: Int = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER
        PsiFormatUtil.formatMethod(method, getPsiSubstitutor(subst, method.getProject, method.getResolveScope),
          PARAM_OPTIONS | PsiFormatUtilBase.SHOW_PARAMETERS, PARAM_OPTIONS)
      }
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

  def getMethodsForName(clazz: PsiClass, name: String): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(clazz).forName(name)._1
          if clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static")) yield n).toSeq
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "apply")
  }

  def getUnapplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    getMethodsForName(clazz, "unapply") ++ getMethodsForName(clazz, "unapplySeq") ++
    (clazz match {
      case c: ScObject => c.objectSyntheticMembers.filter(s => s.name == "unapply" || s.name == "unapplySeq").
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
    while (el != null && classes.find(_.isInstance(el)) == None) el = el.getParent
    el
  }

  /**
   * For one classOf use PsiTreeUtil.getContextOfType instead
   */
  def getContextOfType(element: PsiElement, strict: Boolean, classes: Class[_ <: PsiElement]*): PsiElement = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null
      element.getContext
    }
    while (el != null && classes.find(_.isInstance(el)) == None) el = el.getContext
    el
  }

  def getCompanionModule(clazz: PsiClass): Option[ScTypeDefinition] = {
    getBaseCompanionModule(clazz) match {
      case Some(td) => Some(td)
      case _ =>
        clazz match {
          case x: ScClass if x.isCase => x.fakeCompanionModule
          case _ => None
        }
    }
  }

  def getBaseCompanionModule(clazz: PsiClass): Option[ScTypeDefinition] = {
    if (!clazz.isInstanceOf[ScTypeDefinition]) return None
    val td = clazz.asInstanceOf[ScTypeDefinition]
    val name: String = td.name
    val scope: PsiElement = td.getContext
    val arrayOfElements: Array[PsiElement] = scope match {
      case stub: StubBasedPsiElement[_] if stub.getStub != null =>
        stub.getStub.getChildrenByType(TokenSets.TYPE_DEFINITIONS_SET, JavaArrayFactoryUtil.PsiElementFactory)
      case file: PsiFileImpl =>
        val stub = file.getStub
        if (stub != null) {
          file.getStub.getChildrenByType(TokenSets.TYPE_DEFINITIONS_SET, JavaArrayFactoryUtil.PsiElementFactory)
        } else scope.getChildren
      case _ => scope.getChildren
    }
    td match {
      case _: ScClass | _: ScTrait => {
        arrayOfElements.map((child: PsiElement) =>
          child match {
            case td: ScTypeDefinition => td
            case _ => null: ScTypeDefinition
          }).find((child: ScTypeDefinition) =>
          child.isInstanceOf[ScObject] && child.asInstanceOf[ScObject].name == name)
      }
      case _: ScObject => {
        arrayOfElements.map((child: PsiElement) =>
          child match {
            case td: ScTypeDefinition => td
            case _ => null: ScTypeDefinition
          }).find((child: ScTypeDefinition) =>
          (child.isInstanceOf[ScClass] || child.isInstanceOf[ScTrait])
                  && child.name == name)
      }
      case _ => None
    }
  }

  def hasStablePath(o: PsiNamedElement): Boolean = {
    @tailrec
    def hasStablePathInner(m: PsiMember): Boolean = {
      m.getContext match {
        case f: PsiFile => return true
        case p: ScPackaging => return true
        case _ =>
      }
      m.containingClass match {
        case null => false
        case o: ScObject if o.hasPackageKeyword || o.qualifiedName == "scala.Predef" => true
        case o: ScObject => hasStablePathInner(o)
        case j if j.getLanguage.isInstanceOf[JavaLanguage] => true
        case _ => false
      }
    }

    nameContext(o) match {
      case member: PsiMember => hasStablePathInner(member)
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
        ScType.toPsi(substitutor.subst(ScType.create(`type`, project, scope)), project, scope)
      }

      def substitute(typeParameter: PsiTypeParameter): PsiType = {
        ScType.toPsi(substitutor.subst(new ScTypeParameterType(typeParameter, substitutor)),
          project, scope)
      }

      def putAll(another: PsiSubstitutor): PsiSubstitutor = PsiSubstitutor.EMPTY

      def substituteWithBoundsPromotion(typeParameter: PsiTypeParameter): PsiType = substitute(typeParameter)
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
          import ScalaTokenTypes._
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
  * Underscore functions in ScInfixExpression do need parentheses
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
      import ParserUtils._
      import InfixExpr._
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
              case ScInfixExpr(_, newOper, tuple: ScTuple) =>
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
        case (infix: ScInfixExpr                       , elem: PsiElement) if ScUnderScoreSectionUtil.isUnderscoreFunction(elem) => true
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

  def shouldChangeModificationCount(place: PsiElement): Boolean = {
    var parent = place.getParent
    while (parent != null) {
      parent match {
        case f: ScFunction => f.returnTypeElement match {
          case Some(ret) => return false
          case None => if (!f.hasAssign) return false
        }
        case t: PsiClass => return true
        case bl: ScBlockExprImpl => return bl.shouldChangeModificationCount(null)
        case _ =>
      }
      parent = parent.getParent
    }
    false
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
              .flatMap(stringValueOf(_))
      case _ => None
    }
  }

  /**
   * Finds the n-th parameter from the primiary constructor of `cls`
   */
  def nthConstructorParam(cls: ScClass, n: Int): Option[ScParameter] = cls.constructor match {
    case Some(x: ScPrimaryConstructor) =>
      val clauses = x.parameterList.clauses
      if (clauses.length == 0) None
      else {
        val params = clauses(0).parameters
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
  def parameterOf(exp: ScExpression): Option[Parameter] = {
    exp match {
      case assignment: ScAssignStmt =>
        assignment.getLExpression match {
          case ref: ScReferenceExpression =>
            ref.resolve().asOptionOf[ScParameter].map(p => new Parameter(p))
          case _ => None
        }
      case _ =>
        exp.getParent match {
          case ie: ScInfixExpr if exp == (if (ie.isLeftAssoc) ie.lOp else ie.rOp) =>
            ie.operation match {
              case Resolved(f: ScFunction, _) => f.parameters.headOption.map(p => new Parameter(p))
              case _ => None
            }
          case args: ScArgumentExprList =>
            args.getParent match {
              case constructor: ScConstructor =>
                constructor.reference
                        .flatMap(_.resolve().asOptionOf[ScPrimaryConstructor]) // TODO secondary constructors
                        .flatMap(_.parameters.lift(args.exprs.indexOf(exp)))   // TODO secondary parameter lists
                        .map(p => new Parameter(p))
              case _ => args.matchedParameters.getOrElse(Seq.empty).find(_._1 == exp).map(_._2)
            }
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

  def isByNameArgument(expr: ScExpression): Boolean = {
    expr.getContext match {
      case x: ScArgumentExprList => x.getContext match {
        case mc: ScMethodCall =>
          mc.matchedParameters.find(_._1 == expr).map(_._2) match {
            case Some(param) if param.isByName => true
            case _ => false
          }
        case _ => false
      }
      //todo: maybe it's better to check for concrete parameter if parameters count more than one
      case inf: ScInfixExpr if expr == inf.getArgExpr =>
        val op = inf.operation
        op.bind() match {
          case Some(ScalaResolveResult(fun: ScFunction, _)) =>
            fun.clauses.exists(clause => {
              val clauses = clause.clauses
              if (clauses.length == 0) false
              else !clauses(0).parameters.forall(p => !p.isCallByNameParameter)
            })
          case _ => false
        }
      case _ => false
    }
  }

  def disablePsiBuilderLogger[T](body: => T): T = {
    val logger = diagnostic.Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl")
    logger.setLevel(Level.OFF)
    try {
      body
    } finally {
      logger.setLevel(null)
    }
  }

  /** Creates a synthetic parameter clause based on view and context bounds */
  def syntheticParamClause(paramOwner: ScTypeParametersOwner, paramClauses: ScParameters, classParam: Boolean): Option[ScParameterClause] = {
    if (paramOwner == null) return None

    var i = 0
    def nextName(): String = {
      i += 1
      "evidence$" + i
    }
    def synthParams(typeParam: ScTypeParam): Seq[(String, ScTypeElement => Unit)] = {
      val views = typeParam.viewTypeElement.toSeq.map {
        vte =>
          val code = "%s: _root_.scala.Function1[%s, %s]".format(nextName(), typeParam.name, vte.getText)
          def updateAnalog(typeElement: ScTypeElement) {
            vte.analog = typeElement
          }
          (code, updateAnalog _)
      }
      val bounds = typeParam.contextBoundTypeElement.map {
        (cbte: ScTypeElement) =>
          val code = "%s: %s[%s]".format(nextName(), cbte.getText, typeParam.name)
          def updateAnalog(typeElement: ScTypeElement) {
            cbte.analog = typeElement
          }
          (code, updateAnalog _)
      }
      views ++ bounds
    }
    val params: Seq[(String, (ScTypeElement) => Unit)] = paramOwner.typeParameters match {
      case null => Seq()
      case xs => xs.flatMap(synthParams)
    }
    val clauseText = params.map(_._1).mkString(",")
    if (params.isEmpty) None
    else {
      val fullClauseText: String = "(implicit " + clauseText + ")"
      disablePsiBuilderLogger {
        val paramClause: ScParameterClause = {
          if (classParam) ScalaPsiElementFactory.createImplicitClassParamClauseFromTextWithContext(fullClauseText, paramOwner.getManager, paramClauses)
          else ScalaPsiElementFactory.createImplicitClauseFromTextWithContext(fullClauseText, paramOwner.getManager, paramClauses)
        }
        paramClause.parameters.foreachWithIndex {
          (param, index) =>
            val updateAnalog: (ScTypeElement) => Unit = params(index)._2
            updateAnalog(param.typeElement.get)
            ()
        }
        Some(paramClause)
      }
    }
  }

  def isPossiblyAssignment(ref: PsiReference): Boolean = isPossiblyAssignment(ref.getElement)

  //todo: fix it
  // This is a conservative approximation, we should really resolve the operation
  // to differentiate self assignment from calling a method whose name happens to be an assignment operator.
  def isPossiblyAssignment(elem: PsiElement): Boolean = elem.getContext match {
    case assign: ScAssignStmt if assign.getLExpression == elem => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1 @ ScReferenceExpression.qualifier(`elem`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }

  def availableImportAliases(position: PsiElement): Set[(ScReferenceElement, String)] = {

    def getSelectors(holder: ScImportsHolder): Set[(ScReferenceElement, String)] = {
      val result = collection.mutable.Set[(ScReferenceElement, String)]()
      if (holder != null) {
        val importExprs: Seq[ScImportExpr] = holder.getImportStatements.flatMap(_.importExprs)
        importExprs.flatMap(_.selectors).foreach(s => result += ((s.reference, s.importedName)))
        importExprs.filter(_.selectors.isEmpty).flatMap(_.reference).foreach(ref => result += ((ref, ref.refName)))
        result.toSet
      }
      else Set.empty
    }

    if (position != null && position.getLanguage.getID != "Scala")
      return Set.empty

    var parent = position.getParent
    val aliases = collection.mutable.Set[(ScReferenceElement, String)]()
    while (parent != null) {
      parent match {
        case holder: ScImportsHolder => aliases ++= getSelectors(holder)
        case _ =>
      }
      parent = if (parent.isInstanceOf[PsiFile]) null else parent.getParent
    }

    def correctResolve(alias: (ScReferenceElement, String)): Boolean = {
      val (aliasRef, text) = alias
      val ref = ScalaPsiElementFactory.createReferenceFromText(text, position.getContext, position)
      val resolves = aliasRef.multiResolve(false)
      resolves.exists { rr =>
          ScEquivalenceUtil.smartEquivalence(ref.resolve(), rr.getElement)
      }
    }
    aliases.filter(_._1.getTextRange.getEndOffset < position.getTextOffset).filter(correctResolve).toSet
  }

  def importAliasFor(element: PsiElement, position: PsiElement): Option[ScReferenceElement] = {
    val importAliases = availableImportAliases(position)
    val suitableAliases = importAliases.collect {
      case (aliasRef, aliasName)
        if aliasRef.multiResolve(false).exists(rr => ScEquivalenceUtil.smartEquivalence(rr.getElement, element)) => aliasName
    }
    if (suitableAliases.nonEmpty) {
      val newRef: ScStableCodeReferenceElement = ScalaPsiElementFactory.createReferenceFromText(suitableAliases.head, position.getManager)
      Some(newRef)
    } else None
  }

  def isViableForAssignmentFunction(fun: ScFunction): Boolean = {
    val clauses = fun.paramClauses.clauses
    clauses.length == 0 || (clauses.length == 1 && clauses(0).isImplicit)
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
    if (bindings.size == 1 && expr.exists(_ == instance)) Option(bindings(0))
    else {
      for (bind <- bindings) {
        if (bind.getType(TypingContext.empty).toOption == instance.getType(TypingContext.empty).toOption) return Option(bind)
      }
      None
    }
  }

}
