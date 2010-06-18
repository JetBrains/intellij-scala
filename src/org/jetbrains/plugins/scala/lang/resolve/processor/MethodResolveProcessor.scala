package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import params.{ScParameter, ScTypeParam}
import psi.types._

import nonvalue.{Parameter, TypeParameter, ScTypePolymorphicType, ScMethodType}
import psi.api.base.types.ScTypeElement
import result.{TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import scala.collection.Set
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.api.expr.{ScMethodCall, ScGenericCall}
import psi.implicits.{ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import psi.ScalaPsiUtil
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import Compatibility.Expression
import psi.impl.ScPackageImpl
import caches.CachesUtil

//todo: remove all argumentClauses, we need just one of them
class MethodResolveProcessor(override val ref: PsiElement,
                             refName: String,
                             argumentClauses: List[Seq[Expression]],
                             typeArgElements: Seq[ScTypeElement],
                             kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             expectedOption: => Option[ScType] = None,
                             isUnderscore: Boolean = false) extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    val implFunction: Option[ScFunctionDefinition] = state.get(CachesUtil.IMPLICIT_FUNCTION) match {
      case null => None
      case x => Some(x)
    }
    val implType: Option[ScType] = state.get(CachesUtil.IMPLICIT_TYPE) match {
      case null => None
      case x => Some(x)
    }
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          //all this code for implicit overloading reesolution
          //todo: this is bad code, should be rewrited
          val res = new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType)
          ((candidatesSet ++ levelSet).find(p => p.hashCode == res.hashCode), implicitConversionClass) match {
            case (Some(oldRes: ScalaResolveResult), Some(newClass)) => {
              val oldClass = oldRes.implicitConversionClass
              oldClass match {
                case Some(clazz: PsiClass) if clazz.isInheritor(newClass, true) =>
                case _ => {
                  candidatesSet.remove(oldRes)
                  levelSet.remove(oldRes)
                  levelSet += res
                }
              }
            }
            case _ => addResult(res)
          }
          true
        }
        case cc: ScClass if cc.isCase && ref.getParent.isInstanceOf[ScMethodCall] ||
                ref.getParent.isInstanceOf[ScGenericCall] => {
          addResult(new ScalaResolveResult(cc, s, getImports(state), None, implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && !ref.getParent.isInstanceOf[ScReferenceElement] &&
                ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(cc.constructor.getOrElse(return true), s, getImports(state), None,
            implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
        }
        case cc: ScClass => true
        case o: ScObject if o.isPackageObject => return true // do not resolve to package object
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            addResult(new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass))
          }
          true
        }
        case synthetic: ScSyntheticFunction => {
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), None, implicitConversionClass))
        }
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), None, implicitConversionClass))
        case _ => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
          true
        }
      }
    }
    return true
  }

  private def problemsFor(c: ScalaResolveResult, checkWithImplicits: Boolean): Seq[ApplicabilityProblem] = {
    val substitutor: ScSubstitutor = {
      c.element match {
        case t: ScTypeParametersOwner => {
          c.substitutor.followed(
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
          c.substitutor.followed(
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
        case _ => c.substitutor
      }
    } 

    def checkFunction(fun: PsiNamedElement): Seq[ApplicabilityProblem] = {
      fun match {
        case fun: ScFunction if fun.parameters.length == 0 || isUnderscore => Seq.empty
        case fun: ScFun if fun.paramTypes.length == 0 || isUnderscore => Seq.empty
        case method: PsiMethod if method.getParameterList.getParameters.length == 0 || isUnderscore => Seq.empty
        case _ => {
          expectedOption match {
            case Some(ScFunctionType(retType, params)) => {
              val args = params.map(new Expression(_))
              Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope)._1
            }
            case Some(p@ScParameterizedType(des, typeArgs)) if p.getFunctionType != None => {
              val args = typeArgs.slice(0, typeArgs.length - 1).map(new Expression(_))
              Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope)._1
            }
            case _ => Seq(new MissedParametersClause(null))
          }
        }
      }
    }

    //todo: is it right to think only about function type?
    //this check for defs without parameter clauses and for values
    def checkType(tp: ScType): Seq[ApplicabilityProblem] = {
      tp match {
        case f@ScFunctionType(_, params) => {
          val parameters = params.map(new Parameter("", _, false, false))
          Compatibility.checkConformanceExt(false, parameters, argumentClauses.headOption.getOrElse(Seq.empty),
            checkWithImplicits)._1
        }
        case p@ScParameterizedType(_, typeArgs) if p.getFunctionType != None => {
          val parameters = typeArgs.slice(0, typeArgs.length - 1).map(new Parameter("", _, false, false))
          Compatibility.checkConformanceExt(false, parameters, argumentClauses.headOption.getOrElse(Seq.empty),
            checkWithImplicits)._1
        }
        case _ => return Seq(DoesNotTakeParameters())
      }
    }

    c.element match {
      //Implicit Application
      case f: ScFunction if f.hasMalformedSignature => return Seq(new MalformedDefinition)
      case fun: ScFunction  if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.apply(0).isImplicit &&
              argumentClauses.length == 0 => Seq.empty //special case for cases like Seq.toArray
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        if (fun.isInstanceOf[ScFunction] && fun.asInstanceOf[ScFunction].isConstructor) return Seq(new ApplicabilityProblem("1"))
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      case fun: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      //values
      case b: ScBindingPattern if argumentClauses.length > 0 => {
        checkType(b.getType(TypingContext.empty).getOrElse(Any))
      }
      case p: ScParameter if argumentClauses.length > 0 => {
        checkType(p.getType(TypingContext.empty).getOrElse(Any))
      }
      //empty parameters list: it's like a value
      case tp: ScFunction if tp.parameters.length == 0 && argumentClauses.length > 0 => {
        checkType(tp.getType(TypingContext.empty).getOrElse(Any))
      }
      //simple application including empty application
      case tp: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.typeParameters.length) && tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits, ref.getResolveScope)._1
      }
      case tp: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.getTypeParameters.length) &&
              tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits, ref.getResolveScope)._1
      }
      //for functions => applicaability problem, no type parameters clause
      case method: PsiMethod => Seq(new ApplicabilityProblem("2"))
      case _ => Seq.empty
    }
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    val input: Set[ScalaResolveResult] = candidatesSet ++ levelSet
    
    var mapped = input.map(r => r.copy(problems = problemsFor(r, false)))
    var filtered = mapped.filter(_.isApplicable)
    
    if(filtered.isEmpty) {
      mapped = input.map(r => r.copy(problems = problemsFor(r, true)))
      filtered = mapped.filter(_.isApplicable)
    }

    if (filtered.isEmpty) 
      mapped.toArray.map { r =>    
        if (r.element.isInstanceOf[PsiMethod] || r.element.isInstanceOf[ScFun]) 
          r
        else 
          r.copy(problems = Seq.empty)
      }
    else {
      val len = if (argumentClauses.isEmpty) 0 else argumentClauses(0).length
      val res: Array[T] = MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered) match {
        case Some(r) => Array(r)
        case None => filtered.toArray
      }
      //todo: remove, after scalap fix.
      refName match {
        case "_1" | "_2" | "_3" | "_4" | "_5" | "_6" if res.length > 1 => Array(res.apply(0))
        case _ => res
      }
    }
  }
}
