package org.jetbrains.plugins.scala
package lang
package psi

import api.base._
import api.base.types.{ScRefinement, ScExistentialClause, ScTypeElement}
import api.toplevel.imports.usages.ImportUsed
import api.toplevel.imports.{ScImportStmt, ScImportExpr, ScImportSelector, ScImportSelectors}
import api.toplevel.packaging.{ScPackaging, ScPackageContainer}
import api.toplevel.{ScTypeParametersOwner, ScEarlyDefinitions, ScTypedDefinition}
import api.{ScPackageLike, ScalaFile, ScalaRecursiveElementVisitor}
import com.intellij.psi.scope.PsiScopeProcessor
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef._
import impl.expr.ScBlockExprImpl
import impl.ScalaPsiManager.ClassCategory
import impl.toplevel.typedef.TypeDefinitionMembers
import impl.{ScalaPsiManager, ScalaPsiElementFactory}
import implicits.ScImplicitlyConvertible
import com.intellij.openapi.progress.ProgressManager
import api.expr._
import api.expr.xml.ScXmlExpr

import com.intellij.openapi.project.Project
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import api.statements._
import com.intellij.psi._
import codeStyle.CodeStyleSettingsManager
import com.intellij.psi.search.GlobalSearchScope
import nonvalue.{ScMethodType, Parameter, TypeParameter, ScTypePolymorphicType}
import patterns.{ScBindingPattern, ScReferencePattern, ScCaseClause}
import stubs.ScModifiersStub
import types.Compatibility.Expression
import params._
import parser.parsing.expressions.InfixExpr
import parser.util.ParserUtils
import result.{TypingContext, Success, TypeResult}
import structureView.ScalaElementPresentation
import com.intellij.psi.util._
import formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.roots.{ProjectRootManager, ProjectFileIndex}
import lang.resolve.processor._
import lang.resolve.{ResolveTargets, ResolveUtils, ScalaResolveResult}
import com.intellij.psi.impl.light.LightModifierList
import collection.immutable.Stream
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.openapi.module.{ModuleUtil, Module}
import config.ScalaFacet
import reflect.NameTransformer
import caches.CachesUtil
import java.lang.Exception
import extensions._
import collection.mutable.{ListBuffer, HashSet, ArrayBuffer}
import com.intellij.psi.impl.compiled.ClsFileImpl
import collection.{Set, Seq}
import org.apache.log4j.{Level, Logger}
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diagnostic

/**
 * User: Alexander Podkhalyuzin
 */
object ScalaPsiUtil {
  def isBooleanBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    if (noResolve) {
      s.annotations.find {
        case annot => Set("scala.reflect.BooleanBeanProperty", "reflect.BooleanBeanProperty",
          "BooleanBeanProperty", "scala.beans.BooleanBeanProperty", "beans.BooleanBeanProperty").
          contains(annot.typeElement.getText.replace(" ", ""))
      }.isDefined
    } else {
      s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None ||
        s.hasAnnotation("scala.beans.BooleanBeanProperty") != None
    }
  }

  def isBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean = {
    if (noResolve) {
      s.annotations.find {
        case annot => Set("scala.reflect.BeanProperty", "reflect.BeanProperty",
          "BeanProperty", "scala.beans.BeanProperty", "beans.BeanProperty").
          contains(annot.typeElement.getText.replace(" ", ""))
      }.isDefined
    } else {
      s.hasAnnotation("scala.reflect.BeanProperty") != None ||
        s.hasAnnotation("scala.beans.BeanProperty") != None
    }
  }

  def getDependentItem(element: PsiElement,
                       dep_item: Object = PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT): Option[Object] = {
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
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

  def withEtaExpansion(expr: ScExpression): Boolean = {
    expr.getContext match {
      case call: ScMethodCall => false
      case g: ScGenericCall => withEtaExpansion(g)
      case p: ScParenthesisedExpr => withEtaExpansion(p)
      case _ => true
    }
  }

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
    val module: Module = ModuleUtil.findModuleForPsiElement(element)
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
  def tuplizy(s: Seq[Expression], scope: GlobalSearchScope, manager: PsiManager): Option[Seq[Expression]] = {
    s match {
      case Seq() =>
        // object A { def foo(a: Any) = ()}; A foo () ==>> A.foo(()), or A.foo() ==>> A.foo( () )
        val unitExpr = ScalaPsiElementFactory.createExpressionFromText("()", manager)
        Some(Seq(Expression(unitExpr)))
      case _ =>
        val exprTypes: Seq[ScType] =
          s.map(_.getTypeAfterImplicitConversion(true, false, None)).map {
            case (res, _) => res.getOrAny
          }
        val qual = "scala.Tuple" + exprTypes.length
        val tupleClass = ScalaPsiManager.instance(manager.getProject).getCachedClass(scope, qual)
        if (tupleClass == null)
          None
        else
          Some(Seq(new Expression(ScParameterizedType(ScDesignatorType(tupleClass), exprTypes))))
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

  def findImplicitConversion(e: ScExpression, refName: String, kinds: collection.Set[ResolveTargets.Value],
                             ref: PsiElement, processor: BaseProcessor, noImplicitsForArgs: Boolean):
    Option[(ScType, PsiNamedElement, collection.Set[ImportUsed], ScSubstitutor)] = {
    //TODO! remove this after find a way to improve implicits according to compiler.
    val isHardCoded = refName == "+" &&
      e.getTypeWithoutImplicits(TypingContext.empty).map(_.isInstanceOf[ValType]).getOrElse(false)
    var implicitMap: Seq[(ScType, PsiNamedElement, scala.collection.Set[ImportUsed], ScSubstitutor)] = Seq.empty
    def checkImplicits(secondPart: Boolean, noApplicability: Boolean) {
      lazy val args = processor match {
        case m: MethodResolveProcessor => m.argumentClauses.flatMap(_.map(
          _.getTypeAfterImplicitConversion(false, m.isShapeResolve, None)._1.getOrAny
        ))
        case _ => Seq.empty
      }
      val mp =
        if (noApplicability) e.implicitMap(args = args)._1
        else if (!secondPart) e.implicitMapFirstPart()
        else e.implicitMapSecondPart(args = args)
      implicitMap = mp.flatMap({
        case triple@(t: ScType, fun: PsiNamedElement, importsUsed: collection.Set[ImportUsed], resultSubst) => {
          ProgressManager.checkCanceled()
          if (!isHardCoded || !t.isInstanceOf[ValType]) {
            val newProc = new ResolveProcessor(kinds, ref, refName)
            newProc.processType(t, e, ResolveState.initial)
            val res = !newProc.candidatesS.isEmpty
            if (!noApplicability && res && processor.isInstanceOf[MethodResolveProcessor]) {
              val mrp = processor.asInstanceOf[MethodResolveProcessor]
              val newProc = new MethodResolveProcessor(ref, refName, mrp.argumentClauses, mrp.typeArgElements,
                fun match {
                  case fun: ScFunction if fun.hasTypeParameters => fun.typeParameters.map(tp => TypeParameter(tp.name, Nothing, Any, tp))
                  case _ => Seq.empty
                }, kinds,
                mrp.expectedOption, mrp.isUnderscore, mrp.isShapeResolve, mrp.constructorResolve, noImplicitsForArgs = noImplicitsForArgs)
              val tp = t
              newProc.processType(tp, e, ResolveState.initial)
              val candidates = newProc.candidatesS.filter(_.isApplicable)
              if (candidates.isEmpty) Seq.empty
              else {
                fun match {
                  case fun: ScFunction if fun.hasTypeParameters =>
                    val rr = candidates.iterator.next()
                    rr.resultUndef match {
                      case Some(undef) =>
                        undef.getSubstitutor match {
                          case Some(subst) => Seq((subst.subst(t), fun, importsUsed, resultSubst))
                          case _ => Seq(triple)
                        }
                      case _ => Seq(triple)
                    }
                  case _ => Seq(triple)
                }
              }
            } else {
              if (res) Seq(triple)
              else Seq.empty
            }
          } else Seq.empty
        }
      })
    }
    checkImplicits(false, false)
    if (implicitMap.isEmpty) checkImplicits(true, false)
    if (implicitMap.isEmpty) checkImplicits(false, true)
    if (implicitMap.isEmpty) None
    else if (implicitMap.length == 1) Some(implicitMap.apply(0))
    else MostSpecificUtil(ref, 1).mostSpecificForImplicit(implicitMap.toSet)
  }

  def findCall(place: PsiElement): Option[ScMethodCall] = {
    place.getContext match {
      case call: ScMethodCall => Some(call)
      case p: ScParenthesisedExpr => findCall(p)
      case g: ScGenericCall => findCall(g)
      case _ => None
    }
  }

  def processTypeForUpdateOrApplyCandidates(call: ScMethodCall, tp: ScType, isShape: Boolean,
                                            noImplicits: Boolean): Array[ScalaResolveResult] = {
    import call._
    val isUpdate = call.isUpdateCall
    val methodName = if (isUpdate) "update" else "apply"
    val args: Seq[ScExpression] = call.args.exprs ++ (
            if (isUpdate) getContext.asInstanceOf[ScAssignStmt].getRExpression match {
              case Some(x) => Seq[ScExpression](x)
              case None =>
                Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                  getManager)) //we can't to not add something => add Nothing expression
            }
            else Seq.empty)
    val (expr, exprTp, typeArgs: Seq[ScTypeElement]) = getEffectiveInvokedExpr match {
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
      case ScTypePolymorphicType(internal, tp) if !tp.isEmpty &&
        !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[ScUndefinedType] =>
        processor.processType(internal, getEffectiveInvokedExpr, ResolveState.initial())
        candidates = processor.candidatesS
      case _ =>
    }
    if (candidates.isEmpty) {
      processor.processType(exprTp.inferValueType, getEffectiveInvokedExpr, ResolveState.initial)
      candidates = processor.candidatesS
    }

    if (!noImplicits && candidates.forall(!_.isApplicable)) {
      //should think about implicit conversions
      for ((t, implicitFunction, importsUsed, _) <- expr.implicitMap()._1) {
        ProgressManager.checkCanceled()
        var state = ResolveState.initial.put(ImportUsed.key, importsUsed).
          put(CachesUtil.IMPLICIT_FUNCTION, implicitFunction)
        expr.getClazzForType(t) match {
          case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
          case _ =>
        }
        processor.processType(t, expr, state)
      }
      candidates = processor.candidatesS
    }
    candidates.toArray
  }

  def processTypeForUpdateOrApply(tp: ScType, call: ScMethodCall,
                                  isShape: Boolean): Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[PsiElement])] = {
    import call._

    def checkCandidates(withImplicits: Boolean): Option[(ScType, collection.Set[ImportUsed], Option[PsiNamedElement], Option[PsiElement])] = {
      val candidates: Array[ScalaResolveResult] = processTypeForUpdateOrApplyCandidates(call, tp, isShape, noImplicits = !withImplicits)
      PartialFunction.condOpt(candidates) {
        case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
          val res = fun match {
            case fun: ScFun => (s.subst(fun.polymorphicType), r.importsUsed, r.implicitFunction, Some(fun))
            case fun: ScFunction => (s.subst(fun.polymorphicType), r.importsUsed, r.implicitFunction, Some(fun))
            case meth: PsiMethod => (ResolveUtils.javaPolymorphicType(meth, s, getResolveScope), r.importsUsed,
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
                if (typeParameters.find {
                  case TypeParameter(_, _, _, ptp) if ptp == t.param && ptp.getOwner != ownerPtp.getOwner => true
                  case _ => false
                } != None) res = None
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
    var index: ProjectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    index.getModuleForFile(element.getContainingFile.getVirtualFile)
  }

  def collectImplicitObjects(tp: ScType, place: PsiElement): Seq[ScType] = {
    val projectOpt = Option(place).map(_.getProject)
    val parts: ListBuffer[ScType] = new ListBuffer[ScType]
    def collectParts(tp: ScType, place: PsiElement) {
      tp match {
        case ScCompoundType(comps, _, _, _) => {
          comps.foreach(collectParts(_, place))
        }
        case p@ScParameterizedType(a: ScAbstractType, args) => {
          collectParts(a, place)
          args.foreach(collectParts(_, place))
        }
        case p@ScParameterizedType(des, args) => {
          ScType.extractClass(p, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
          args.foreach(collectParts(_, place))
        }
        case j: JavaArrayType => {
          val parameterizedType = j.getParameterizedType(place.getProject, place.getResolveScope)
          collectParts(parameterizedType.getOrElse(return), place)
        }
        case f@ScFunctionType(retType, params) => {
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
          params.foreach(collectParts(_, place))
          collectParts(retType, place)
        }
        case f@ScTupleType(params) => {
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
          params.foreach(collectParts(_, place))
        }
        case proj@ScProjectionType(projected, _, _, _) => {
          collectParts(projected, place)
          ScType.extractClass(tp, projectOpt) match {
            case Some(pair) => parts += tp
            case _ =>
          }
        }
        case ScAbstractType(_, lower, upper) => {
          collectParts(lower, place)
          collectParts(upper, place)
        }
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
    val res: HashSet[ScType] = new HashSet
    for (part <- parts) {
      //here we want to convert projection types to right projections
      val visited = new HashSet[PsiClass]()
      def collectObjects(tp: ScType, update: Option[ScSubstitutor] = None) {
        tp match {
          case Any => return
          case tp: StdType if Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char").contains(tp.name) =>
            val obj = ScalaPsiManager.instance(place.getProject).
              getCachedClass("scala." + tp.name, place.getResolveScope, ClassCategory.OBJECT)
            obj match {
              case o: ScObject => res += ScDesignatorType(o)
              case _ =>
            }
            return
          case ScDesignatorType(ta: ScTypeAliasDefinition) =>
            collectObjects(ta.aliasedType.getOrAny, update)
            return
          case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
            collectObjects(p.actualSubst.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny), update)
            return
          case ScParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) => {
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))), args)
            collectObjects(genericSubst.subst(ta.aliasedType.getOrAny), update)
            return
          }
          case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] => {
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(p.actualElement.asInstanceOf[ScTypeAliasDefinition].typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp)
              )), args)
            val s = p.actualSubst.followed(genericSubst)
            collectObjects(s.subst(p.actualElement.asInstanceOf[ScTypeAliasDefinition].
              aliasedType.getOrAny), update)
            return
          }
          case _ =>
        }
        ScType.extractClass(tp, projectOpt) match {
          case Some(clazz: PsiClass) if !visited.contains(clazz) =>
            clazz match {
              case o: ScObject => res += tp
              case _ =>
                getCompanionModule(clazz) match {
                  case Some(obj: ScObject) =>
                    tp match {
                      case ScProjectionType(proj, _, subst, s) =>
                        res += update.getOrElse(ScSubstitutor.empty).subst(ScProjectionType(proj, obj, subst, s))
                      case ScParameterizedType(ScProjectionType(proj, _, subst, s), _) =>
                        res += update.getOrElse(ScSubstitutor.empty).subst(ScProjectionType(proj, obj, subst, s))
                      case _ =>
                        res += ScDesignatorType(obj)
                    }
                  case _ =>
                }
            }
            val newSubst = tp match {
              case p: ScProjectionType => Some(p.actualSubst)
              case ScParameterizedType(p: ScProjectionType, _) => Some(p.actualSubst)
              case _ => None
            }
            clazz match {
              case td: ScTemplateDefinition =>
                td.superTypes.foreach((tp: ScType) => {
                  visited += clazz
                  collectObjects(tp, newSubst)
                  visited -= clazz
                })
              case clazz: PsiClass =>
                clazz.getSuperTypes.foreach(tp => {
                  val stp = ScType.create(tp, place.getProject, place.getResolveScope)
                  visited += clazz
                  collectObjects(stp, newSubst)
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
          ScType.create(p.getType, p.getProject, paramTopLevel = true, treatJavaObjectAsAny = treatJavaObjectAsAny)
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
            f.getDesugarisedExpr match {
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
        case tp: ScTypeParam => {
          " in:" + tp.getContainingFileName + ":" + tp.getOffsetInFile
        }
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
                         filterTypeParams: Boolean = true): ScTypePolymorphicType = {
    localTypeInferenceWithApplicability(retType, params, exprs, typeParams, shouldUndefineParameters, safeCheck,
      filterTypeParams)._1
  }


  class SafeCheckException extends Exception


  //todo: move to InferUtil
  def localTypeInferenceWithApplicability(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                          typeParams: Seq[TypeParameter],
                                          shouldUndefineParameters: Boolean = true,
                                          safeCheck: Boolean = false,
                                          filterTypeParams: Boolean = true): (ScTypePolymorphicType, Seq[ApplicabilityProblem]) = {
    val (tp, problems, _) = localTypeInferenceWithApplicabilityExt(retType, params, exprs, typeParams,
      shouldUndefineParameters, safeCheck, filterTypeParams)
    (tp, problems)
  }

  def localTypeInferenceWithApplicabilityExt(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                             typeParams: Seq[TypeParameter],
                                             shouldUndefineParameters: Boolean = true,
                                             safeCheck: Boolean = false,
                                             filterTypeParams: Boolean = true
    ): (ScTypePolymorphicType, Seq[ApplicabilityProblem], Seq[(Parameter, ScExpression)]) = {
    // See SCL-3052, SCL-3058
    // This corresponds to use of `isCompatible` in `Infer#methTypeArgs` in scalac, where `isCompatible` uses `weak_<:<`
    val s: ScSubstitutor = if (shouldUndefineParameters) undefineSubstitutor(typeParams) else ScSubstitutor.empty
    val abstractSubst = ScTypePolymorphicType(retType, typeParams).abstractTypeSubstitutor
    val paramsWithUndefTypes = params.map(p => p.copy(paramType = s.subst(p.paramType),
      expectedType = abstractSubst.subst(p.paramType)))
    val c = Compatibility.checkConformanceExt(true, paramsWithUndefTypes, exprs, true, false)
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

              if (safeCheck && !undefiningSubstitutor.subst(lower).conforms(undefiningSubstitutor.subst(upper), true))
                throw new SafeCheckException
              TypeParameter(tp.name, lower, upper, tp.ptp)
            }))
          } else {
            typeParams.foreach {case tp =>
              val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
              if (un.lowerMap.contains(name) || un.upperMap.contains(name)) {
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
                    un = un.addLower(name, substedLowerType)
                  }
                }
                if (tp.upperType != Any) {
                  val substedUpperType = unSubst.subst(tp.upperType)
                  if (!hasRecursiveTypeParameters(substedUpperType)) {
                    un = un.addUpper(name, substedUpperType)
                  }
                }
              }
            }
            un.getSubstitutor match {
              case Some(unSubst) =>
                ScTypePolymorphicType(unSubst.subst(retType), typeParams.filter {case tp =>
                  val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
                  !un.lowerMap.contains(name) && !un.upperMap.contains(name)
                }.map(tp => TypeParameter(tp.name, unSubst.subst(tp.lowerType), unSubst.subst(tp.upperType), tp.ptp)))
              case _ =>
                ScTypePolymorphicType(unSubst.subst(retType), typeParams.filter {case tp =>
                  val name = (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp))
                  !un.lowerMap.contains(name) && !un.upperMap.contains(name)
                }.map(tp => TypeParameter(tp.name, unSubst.subst(tp.lowerType), unSubst.subst(tp.upperType), tp.ptp)))
            }
          }
        case None => throw new SafeCheckException
      }
    } else ScTypePolymorphicType(retType, typeParams)
    (tpe, c.problems, c.matchedArgs)
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
      while (parent.getTextRange.equalsToRange(prev.getTextRange.getStartOffset, prev.getTextRange.getEndOffset)) {
        prev = parent
        parent = parent.getParent
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
    def inner(parent: PsiElement, k: List[PsiElement] => List[PsiElement]): List[PsiElement] = {
      if (parent != topLevel && parent != null)
        inner(parent.getParent, {l => parent :: k(l)})
      else k(Nil)
    }
    inner(elem, (l: List[PsiElement]) => l)
  }

  //todo: always returns true?!
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
      case Some(x) => x.supers.map {_.info}
      case None =>
        throw new RuntimeException("internal error: could not find val matching: \n%s\n\nin class: \n%s".format(
          x.getText, clazz.getText
        ))
    }


    val beanMethods = typed.getBeanMethods
    beanMethods.foreach {method =>
      val sigs = TypeDefinitionMembers.getSignatures(clazz).forName(method.name)._1
      (sigs.get(new PhysicalSignature(method, ScSubstitutor.empty)): @unchecked) match {
        //partial match
        case Some(x) => res ++= (x.supers.map {_.info})
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
    for (child <- element.getChildren) {
      child match {
        case x: ScStableCodeReferenceElement => x.resolve() match {
          case clazz: PsiClass =>
            x.replace(ScalaPsiElementFactory.createReferenceFromText(clazz.getName, clazz.getManager)).
                    asInstanceOf[ScStableCodeReferenceElement].bindToElement(clazz)
          case m: ScTypeAlias if m.containingClass != null && (
                  m.containingClass.qualifiedName == "scala.Predef" ||
                  m.containingClass.qualifiedName == "scala") => {
            x.replace(ScalaPsiElementFactory.createReferenceFromText(m.name, m.getManager)).
                    asInstanceOf[ScStableCodeReferenceElement].bindToElement(m)
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
        ScalaElementPresentation.getMethodPresentableText(method, false, subst)
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

  def hasStablePath(o: ScObject): Boolean = {
    o.getContext match {
      case f: ScalaFile => return true
      case p: ScPackaging => return true
      case _ =>
    }
    o.containingClass match {
      case null => false
      case o: ScObject => hasStablePath(o)
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

  //todo: rewrite this!
  def needParentheses(from: ScExpression, expr: ScExpression): Boolean = {
    val parent = from.getParent
    (parent, expr) match { //true only for other cases
      case _ if !parent.isInstanceOf[ScExpression] => false
      case (_: ScTuple, _) => false
      case (_: ScReferenceExpression, _: ScBlockExpr) => false
      case (_: ScReferenceExpression, _: ScNewTemplateDefinition) if expr.getText.endsWith(")") ||
              expr.getText.endsWith("}") || expr.getText.endsWith("]") => false
      case (_: ScReferenceExpression, _: ScReferenceExpression) => false
      case (_: ScReferenceExpression, _: ScGenericCall) => false
      case (_: ScReferenceExpression, _: ScMethodCall) => false
      case (_: ScReferenceExpression, _: ScLiteral) => false
      case (_: ScReferenceExpression, _) if expr.getText == "_" => false
      case (_: ScReferenceExpression, _: ScTuple) => false
      case (_: ScReferenceExpression, _: ScXmlExpr) => false
      case (_: ScReferenceExpression, _: ScParenthesisedExpr) => false
      case (_: ScReferenceExpression, _: ScThisReference) => false
      case (_: ScReferenceExpression, _) => true
      case (_: ScGenericCall, _: ScReferenceExpression) => false
      case (_: ScGenericCall, _: ScMethodCall) => false
      case (_: ScGenericCall, _: ScLiteral) => false
      case (_: ScGenericCall, _: ScTuple) => false
      case (_: ScGenericCall, _: ScXmlExpr) => false
      case (_: ScGenericCall, _) if expr.getText == "_" => false
      case (_: ScGenericCall, _: ScBlockExpr) => false
      case (_: ScGenericCall, _) => true
      case (_: ScMethodCall, _: ScReferenceExpression) => false
      case (_: ScMethodCall, _: ScMethodCall) => false
      case (_: ScMethodCall, _: ScGenericCall) => false
      case (_: ScMethodCall, _: ScLiteral) => false
      case (_: ScMethodCall, _) if expr.getText == "_" => false
      case (_: ScMethodCall, _: ScXmlExpr) => false
      case (_: ScMethodCall, _: ScTuple) => false
      case (_: ScMethodCall, _) => true
      case (_: ScUnderscoreSection, _: ScReferenceExpression) => false
      case (_: ScUnderscoreSection, _: ScMethodCall) => false
      case (_: ScUnderscoreSection, _: ScGenericCall) => false
      case (_: ScUnderscoreSection, _: ScLiteral) => false
      case (_: ScUnderscoreSection, _) if expr.getText == "_" => false
      case (_: ScUnderscoreSection, _: ScXmlExpr) => false
      case (_: ScUnderscoreSection, _: ScTuple) => false
      case (_: ScUnderscoreSection, _) => true
      case (_: ScBlock, _) => false
      case (_: ScPrefixExpr, _: ScBlockExpr) => false
      case (_: ScPrefixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPrefixExpr, _: ScUnderscoreSection) => false
      case (_: ScPrefixExpr, _: ScReferenceExpression) => false
      case (_: ScPrefixExpr, _: ScGenericCall) => false
      case (_: ScPrefixExpr, _: ScMethodCall) => false
      case (_: ScPrefixExpr, _: ScLiteral) => false
      case (_: ScPrefixExpr, _) if expr.getText == "_" => false
      case (_: ScPrefixExpr, _: ScTuple) => false
      case (_: ScPrefixExpr, _: ScXmlExpr) => false
      case (_: ScPrefixExpr, _) => true
      case (_: ScInfixExpr, _: ScBlockExpr) => false
      case (_: ScInfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScInfixExpr, _: ScUnderscoreSection) => false
      case (_: ScInfixExpr, _: ScReferenceExpression) => false
      case (_: ScInfixExpr, _: ScGenericCall) => false
      case (_: ScInfixExpr, _: ScMethodCall) => false
      case (_: ScInfixExpr, _: ScLiteral) => false
      case (_: ScInfixExpr, _) if expr.getText == "_" => false
      case (_: ScInfixExpr, _: ScTuple) => false
      case (_: ScInfixExpr, _: ScXmlExpr) => false
      case (_: ScInfixExpr, _: ScPrefixExpr) => false
      case (_: ScInfixExpr, _: ScParenthesisedExpr) => false
      case (_: ScInfixExpr, _: ScThisReference) => false
      case (par: ScInfixExpr, child: ScInfixExpr) => {
        import ParserUtils._
        import InfixExpr._
        if (par.lOp == from) {
          val lid = par.operation.getText
          val rid = child.operation.getText
          if (priority(lid) < priority(rid)) true
          else if (priority(rid) < priority(lid)) false
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) true
          else false
        }
        else {
          val lid = child.operation.getText
          val rid = par.operation.getText
          if (priority(lid) < priority(rid)) false
          else if (priority(rid) < priority(lid)) true
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) false
          else true
        }
      }
      case (_: ScInfixExpr, _) => true
      case (_: ScPostfixExpr, _: ScBlockExpr) => false
      case (_: ScPostfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPostfixExpr, _: ScUnderscoreSection) => false
      case (_: ScPostfixExpr, _: ScReferenceExpression) => false
      case (_: ScPostfixExpr, _: ScGenericCall) => false
      case (_: ScPostfixExpr, _: ScMethodCall) => false
      case (_: ScPostfixExpr, _: ScLiteral) => false
      case (_: ScPostfixExpr, _) if expr.getText == "_" => false
      case (_: ScPostfixExpr, _: ScTuple) => false
      case (_: ScPostfixExpr, _: ScXmlExpr) => false
      case (_: ScPostfixExpr, _: ScPrefixExpr) => false
      case (_: ScPostfixExpr, _) => true
      case (_: ScTypedStmt, _: ScBlockExpr) => false
      case (_: ScTypedStmt, _: ScNewTemplateDefinition) => false
      case (_: ScTypedStmt, _: ScUnderscoreSection) => false
      case (_: ScTypedStmt, _: ScReferenceExpression) => false
      case (_: ScTypedStmt, _: ScGenericCall) => false
      case (_: ScTypedStmt, _: ScMethodCall) => false
      case (_: ScTypedStmt, _: ScLiteral) => false
      case (_: ScTypedStmt, _) if expr.getText == "_" => false
      case (_: ScTypedStmt, _: ScTuple) => false
      case (_: ScTypedStmt, _: ScXmlExpr) => false
      case (_: ScTypedStmt, _: ScPrefixExpr) => false
      case (_: ScTypedStmt, _: ScInfixExpr) => false
      case (_: ScTypedStmt, _: ScPostfixExpr) => false
      case (_: ScTypedStmt, _) => true
      case (_: ScMatchStmt, _: ScBlockExpr) => false
      case (_: ScMatchStmt, _: ScNewTemplateDefinition) => false
      case (_: ScMatchStmt, _: ScUnderscoreSection) => false
      case (_: ScMatchStmt, _: ScReferenceExpression) => false
      case (_: ScMatchStmt, _: ScGenericCall) => false
      case (_: ScMatchStmt, _: ScMethodCall) => false
      case (_: ScMatchStmt, _: ScLiteral) => false
      case (_: ScMatchStmt, _) if expr.getText == "_" => false
      case (_: ScMatchStmt, _: ScTuple) => false
      case (_: ScMatchStmt, _: ScXmlExpr) => false
      case (_: ScMatchStmt, _: ScPrefixExpr) => false
      case (_: ScMatchStmt, _: ScInfixExpr) => false
      case (_: ScMatchStmt, _: ScPostfixExpr) => false
      case (_: ScMatchStmt, _) => true
      case _ => false
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
              .flatMap(_.resolve.asOptionOf[ScBindingPattern])
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
              case _ => args.matchedParameters.getOrElse(Map.empty).get(exp)
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
    if(e.isInstanceOf[ScClassParameter]) {
      return e.asInstanceOf[ScClassParameter].isVal
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
          mc.matchedParameters.get(expr) match {
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
            fun.clauses.map(clause => {
              val clauses = clause.clauses
              if (clauses.length == 0) false
              else !clauses(0).parameters.forall(p => !p.isCallByNameParameter)
            }).getOrElse(false)
          case _ => false
        }
      case _ => false
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
      val logger = diagnostic.Logger.getInstance(classOf[PsiBuilderImpl])
      logger.setLevel(Level.OFF)
      try {
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
      } finally {
        logger.setLevel(null)
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
}
