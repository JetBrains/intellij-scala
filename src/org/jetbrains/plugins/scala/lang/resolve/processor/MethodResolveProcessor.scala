package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.statements._
import com.intellij.psi._
import params.ScTypeParam
import psi.types._

import psi.api.base.types.ScTypeElement
import result.TypingContext
import scala._
import collection.immutable.HashSet
import scala.collection.Set
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.implicits.ScImplicitlyConvertible
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.impl.ScPackageImpl
import caches.CachesUtil
import psi.types.Compatibility.{ConformanceExtResult, Expression}
import psi.{ScalaPsiElement, ScalaPsiUtil}
import psi.api.expr._
import psi.api.base.ScPrimaryConstructor

//todo: remove all argumentClauses, we need just one of them
class MethodResolveProcessor(override val ref: PsiElement,
                             val refName: String,
                             var argumentClauses: List[Seq[Expression]],
                             val typeArgElements: Seq[ScTypeElement],
                             override val kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             val expectedOption: () => Option[ScType] = () => None,
                             val isUnderscore: Boolean = false,
                             var isShapeResolve: Boolean = false,
                             val constructorResolve: Boolean = false,
                             val enableTupling: Boolean = false) extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    def implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY).toOption
    def implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION).toOption
    def implType: Option[ScType] = state.get(CachesUtil.IMPLICIT_TYPE).toOption
    def isNamedParameter: Boolean = state.get(CachesUtil.NAMED_PARAM_KEY).toOption.map(_.booleanValue).getOrElse(false)
    def fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY).toOption
    if (nameAndKindMatch(named, state) || constructorResolve) {
      if (!isAccessible(named, ref)) return true
      val s = fromType match {
        case Some(tp) => getSubst(state).addUpdateThisType(tp)
        case _ => getSubst(state)
      }
      element match {
        case m: PsiMethod =>
          addResult(new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType))
        case cc: ScClass =>
        case o: ScObject if o.isPackageObject =>  // do not resolve to package object
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] =>
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            addResult(new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass,
              implicitFunction = implFunction, implicitType = implType, fromType = fromType))
          }
        case synthetic: ScSyntheticFunction =>
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType))
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType))
        case _ =>
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType))
      }
    }
    return true
  }



  override def candidatesS: Set[ScalaResolveResult] = {
    val input: Set[ScalaResolveResult] = candidatesSet ++ levelSet
    if (!isShapeResolve && enableTupling && argumentClauses.length > 0 && argumentClauses.apply(0).length > 1) {
      isShapeResolve = true
      val cand1 = MethodResolveProcessor.candidates(this, input)
      if (cand1.size == 0 || cand1.forall(_.tuplingUsed)) {
        //tupling ok
        isShapeResolve = false
        val oldArg = argumentClauses
        val tpl = ScalaPsiUtil.tuplizy(argumentClauses.apply(0), ref.getProject, ref.getResolveScope)
        if (tpl == None) {
          return MethodResolveProcessor.candidates(this, input)
        }
        argumentClauses = tpl.toList
        val res = MethodResolveProcessor.candidates(this, input)
        argumentClauses = oldArg
        if (res.forall(!_.isApplicable)) {
          return MethodResolveProcessor.candidates(this, input)
        }
        res.map(r => r.copy(tuplingUsed = true))
      } else {
        isShapeResolve = false
        MethodResolveProcessor.candidates(this, input)
      }
    } else
      MethodResolveProcessor.candidates(this, input)
  }
}

object MethodResolveProcessor {
  private def problemsFor(c: ScalaResolveResult, checkWithImplicits: Boolean,
                          proc: MethodResolveProcessor): ConformanceExtResult = {
    import proc._
    val realResolveResult = c.innerResolveResult match {
      case Some(rr) => rr
      case _ => c
    }
    val element = realResolveResult.element
    val s = realResolveResult.substitutor

    val substitutor: ScSubstitutor = {
      element match {
        case t: ScTypeParametersOwner => {
          s.followed(
          if (typeArgElements.length  != 0 && t.typeParameters.length == typeArgElements.length ) {
            ScalaPsiUtil.genericCallSubstitutor(t.typeParameters.map(p =>
              (p.name, ScalaPsiUtil.getPsiElementId(p))), typeArgElements)
          } else {
            t.typeParameters.foldLeft(ScSubstitutor.empty) {
              (subst: ScSubstitutor, tp: ScTypeParam) =>
                subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)),
                  new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty)))
            }
          })
        }
        case p: PsiTypeParameterListOwner => {
          s.followed(
          if (typeArgElements.length  != 0 && p.getTypeParameters.length == typeArgElements.length) {
            ScalaPsiUtil.genericCallSubstitutor(p.getTypeParameters.map(p =>
              (p.getName, ScalaPsiUtil.getPsiElementId(p))), typeArgElements)
          } else {
            p.getTypeParameters.foldLeft(ScSubstitutor.empty) {
              (subst: ScSubstitutor, tp: PsiTypeParameter) =>
                subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)),
                  new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty)))
            }
          })
        }
        case _ => s
      }
    }

    def checkFunction(fun: PsiNamedElement): ConformanceExtResult = {
      expectedOption() match {
        case Some(ScFunctionType(retType, params)) => {
          val args = params.map(new Expression(_))
          Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope, isShapeResolve)
        }
        case Some(p@ScParameterizedType(des, typeArgs)) if p.getFunctionType != None => {
          val args = typeArgs.slice(0, typeArgs.length - 1).map(new Expression(_))
          Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope, isShapeResolve)
        }
        case _ => {
          fun match {
            case fun: ScFunction if fun.paramClauses.clauses.length == 0 ||
                    fun.paramClauses.clauses.apply(0).parameters.length == 0 ||
                    isUnderscore => ConformanceExtResult(Seq.empty)
            case fun: ScFun if fun.parameters.length == 0 || isUnderscore => ConformanceExtResult(Seq.empty)
            case c: ScPrimaryConstructor
              if(c.parameterList.clauses.headOption.map(_.isImplicit).getOrElse(false)) =>
              ConformanceExtResult(Seq.empty)
            case method: PsiMethod if method.getParameterList.getParameters.length == 0 ||
                    isUnderscore => ConformanceExtResult(Seq.empty)
            case _ => ConformanceExtResult(Seq(MissedParametersClause(null)))
          }
        }
      }
    }



    element match {
      //objects
      case obj: PsiClass => return ConformanceExtResult(Seq.empty)
      //Implicit Application
      case f: ScFunction if f.hasMalformedSignature => return ConformanceExtResult(Seq(new MalformedDefinition))
      case c: ScPrimaryConstructor if c.hasMalformedSignature => return ConformanceExtResult(Seq(new MalformedDefinition))
      case fun: ScFunction  if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.apply(0).isImplicit &&
              argumentClauses.length == 0 => ConformanceExtResult(Seq.empty) //special case for cases like Seq.toArray
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        if (fun.isInstanceOf[ScFunction] && fun.asInstanceOf[ScFunction].isConstructor)
          return ConformanceExtResult(Seq(new ApplicabilityProblem("1")))
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      case fun: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      //simple application including empty application
      case tp: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.typeParameters.length) && tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits,
          ref.getResolveScope, isShapeResolve)
      }
      case tp: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.getTypeParameters.length) &&
              tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits,
          ref.getResolveScope, isShapeResolve)
      }
      //for functions => applicaability problem, no type parameters clause
      case method: PsiMethod => ConformanceExtResult(Seq(new ApplicabilityProblem("2")))
      case _ => ConformanceExtResult(Seq.empty)
    }
  }

  def candidates(proc: MethodResolveProcessor, input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    import proc._

    def expand(r: ScalaResolveResult): Set[ScalaResolveResult] = {
      r.element match {
        case f: ScFunction if f.hasParameterClause => HashSet(r)
        case b: ScTypedDefinition if argumentClauses.length > 0 => {
          val tp = r.substitutor subst b.getType(TypingContext.empty).getOrElse(return HashSet.empty)
          val processor = new CollectMethodsProcessor(ref, "apply")
          processor.processType(tp, ref.asInstanceOf[ScalaPsiElement])
          processor.candidatesS.map(rr => r.copy(innerResolveResult = Some(rr)))
        }
        case _ => HashSet(r)
      }
    }

    def mapper(applicationImplicits: Boolean): Set[ScalaResolveResult] = {
      if (argumentClauses.length > 0) {
        input.flatMap(expand).map {
          r => {
            val pr = problemsFor(r, applicationImplicits, proc)
            r.innerResolveResult match {
              case Some(rr) => {
                r.copy(innerResolveResult = Some(rr.copy(problems = pr.problems,
                  defaultParameterUsed = pr.defaultParameterUsed)))
              }
              case _ => r.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed)
            }
          }
        }
      } else input.map(r => {
        val pr = problemsFor(r, applicationImplicits, proc)
        r.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed)
      })
    }
    var mapped = mapper(false)
    var filtered = mapped.filter(_.isApplicableInternal)

    if (filtered.isEmpty) {
      //check with implicits
      mapped = mapper(true)
      filtered = mapped.filter(_.isApplicableInternal)
    }

    val onlyValues = mapped.forall(_.isApplicable)
    if (filtered.isEmpty && onlyValues) {
      //possible implicit conversions in ScMethodCall
      return input
    } else if (!onlyValues) {
      //in this case all values are not applicable
      mapped = mapped.map(r => {
        if (r.isApplicable) {
          r.innerResolveResult match {
            case Some(rr) => r.copy(problems = rr.problems)
            case _ => r
          }
        }
        else r
      })
    }

    //remove default parameters alternatives
    if (filtered.size > 1) filtered = filtered.filter(r => r.innerResolveResult match {
      case Some(rr) => !rr.defaultParameterUsed
      case None => !r.defaultParameterUsed
    })

    if (isShapeResolve) {
      if (filtered.isEmpty) {
        if (enableTupling) {
          val filtered2 = input.filter(r => {
            r.element match {
              case fun: ScFunction if fun.paramClauses.clauses.length > 0 =>
                fun.paramClauses.clauses.apply(0).parameters.length == 1
              case p: ScPrimaryConstructor if p.parameterList.clauses.length > 0 =>
                p.parameterList.clauses.apply(0).parameters.length == 1
              case m: PsiMethod => m.getParameterList.getParameters.length == 1
              case _ => false
            }
          }).map(r => r.copy(tuplingUsed = true))
          if (filtered2.isEmpty) return input
          return filtered2
        }
        return input
      }
      else return filtered
    }

    if (filtered.isEmpty && mapped.isEmpty) input
    else if (filtered.isEmpty) mapped
    else {
      val len = if (argumentClauses.isEmpty) 0 else argumentClauses(0).length
      MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered.toSet) match {
        case Some(r) => HashSet(r)
        case None => filtered
      }
    }
  }
}
