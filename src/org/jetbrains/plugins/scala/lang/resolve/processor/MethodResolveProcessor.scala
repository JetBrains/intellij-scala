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
import psi.implicits.{ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import psi.impl.ScPackageImpl
import caches.CachesUtil
import psi.types.Compatibility.{ConformanceExtResult, Expression}
import psi.{ScalaPsiElement, ScalaPsiUtil}
import psi.api.expr._
//todo: remove all argumentClauses, we need just one of them
class MethodResolveProcessor(override val ref: PsiElement,
                             val refName: String,
                             val argumentClauses: List[Seq[Expression]],
                             val typeArgElements: Seq[ScTypeElement],
                             override val kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             val expectedOption: () => Option[ScType] = () => None,
                             val isUnderscore: Boolean = false,
                             val isShapeResolve: Boolean = false) extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    val implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION) match {
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
          addResult(new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
          true
        }
        case cc: ScClass if cc.isCase && ref.getParent.isInstanceOf[ScMethodCall] ||
                ref.getParent.isInstanceOf[ScGenericCall] => {
          addResult(new ScalaResolveResult(cc, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType,
            innerResolveResult = Some(new ScalaResolveResult(cc.constructor.getOrElse(return true), s, getImports(state),
              None, implicitConversionClass, implicitFunction = implFunction, implicitType = implType))))
          true
        }
        case cc: ScClass if cc.isCase && !ref.getParent.isInstanceOf[ScReferenceElement] &&
                ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(cc.constructor.getOrElse(return true), s, getImports(state), None,
            implicitConversionClass, implicitFunction = implFunction, implicitType = implType))
          true
        }
        case cc: ScClass if cc.isCase && ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
        }
        case cc: ScClass => true
        case o: ScObject if o.isPackageObject => return true // do not resolve to package object
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            addResult(new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass,
              implicitFunction = implFunction, implicitType = implType))
          }
          true
        }
        case synthetic: ScSyntheticFunction => {
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
        }
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
        case _ => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
          true
        }
      }
    }
    return true
  }



  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    val input: Set[ScalaResolveResult] = candidatesSet ++ levelSet
    MethodResolveProcessor.candidates(this, input)
  }
}

object MethodResolveProcessor {
  private def problemsFor(c: ScalaResolveResult, checkWithImplicits: Boolean, proc: MethodResolveProcessor): ConformanceExtResult = {
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
      fun match {
        case fun: ScFunction if isUnderscore => ConformanceExtResult(Seq.empty)
        case fun: ScFun if isUnderscore => ConformanceExtResult(Seq.empty)
        case fun: PsiMethod if isUnderscore => ConformanceExtResult(Seq.empty)
        case _ => {
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
                        fun.paramClauses.clauses.apply(0).parameters.length == 0 || isUnderscore => ConformanceExtResult(Seq.empty)
                case fun: ScFun if fun.paramTypes.length == 0 || isUnderscore => ConformanceExtResult(Seq.empty)
                case method: PsiMethod if method.getParameterList.getParameters.length == 0 ||
                        isUnderscore => ConformanceExtResult(Seq.empty)
                case _ => ConformanceExtResult(Seq(new MissedParametersClause(null)))
              }
            }
          }
        }
      }
    }


    
    element match {
      //objects
      case obj: PsiClass => return ConformanceExtResult(Seq.empty)
      //Implicit Application
      case f: ScFunction if f.hasMalformedSignature => return ConformanceExtResult(Seq(new MalformedDefinition))
      case fun: ScFunction  if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.apply(0).isImplicit &&
              argumentClauses.length == 0 => ConformanceExtResult(Seq.empty) //special case for cases like Seq.toArray
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        if (fun.isInstanceOf[ScFunction] && fun.asInstanceOf[ScFunction].isConstructor) return ConformanceExtResult(Seq(new ApplicabilityProblem("1")))
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

  def candidates[T >: ScalaResolveResult : ClassManifest](proc: MethodResolveProcessor,
                                                                   input: Set[ScalaResolveResult]): Array[T] = {
    import proc._

    def expand(r: ScalaResolveResult): Seq[ScalaResolveResult] = {
      r.element match {
        case f: ScFunction if f.hasParameterClause => Seq(r)
        case b: ScTypedDefinition if argumentClauses.length > 0 => {
          val tp = r.substitutor subst b.getType(TypingContext.empty).getOrElse(return Seq.empty)
          val processor = new CollectMethodsProcessor(ref, "apply")
          processor.processType(tp, ref.asInstanceOf[ScalaPsiElement])
          processor.candidates.map(rr => r.copy(innerResolveResult = Some(rr)))
        }
        case _ => Seq(r)
      }
    }

    def mapper(applicationImplicits: Boolean): Seq[ScalaResolveResult] = {
      if (argumentClauses.length > 0) {
        input.toSeq.flatMap(expand).map {
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
      } else input.toSeq.map(r => {
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
      return input.toArray
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
      if (filtered.isEmpty) return input.toArray
      else return filtered.toArray
    }

    if (filtered.isEmpty && mapped.isEmpty) input.toArray
    else if (filtered.isEmpty) mapped.toArray
    else {
      val len = if (argumentClauses.isEmpty) 0 else argumentClauses(0).length
      val res: Array[T] = MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered.toSet) match {
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
