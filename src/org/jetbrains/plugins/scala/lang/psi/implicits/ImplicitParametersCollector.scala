package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import processor.{ImplicitProcessor, MostSpecificUtil}
import result.{Success, TypingContext}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import params.{ScParameterClause, ScParameter, ScTypeParam}
import util.PsiTreeUtil
import collection.immutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 * @param concreteType The type from which to determine the implicit scope.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitParametersCollector(place: PsiElement, tp: ScType) {
  def collect: Seq[ScalaResolveResult] = {
    val processor = new ImplicitParametersProcessor
    def treeWalkUp(placeForTreeWalkUp: PsiElement, lastParent: PsiElement) {
      if (placeForTreeWalkUp == null) return
      if (!placeForTreeWalkUp.processDeclarations(processor,
        ResolveState.initial(), lastParent, place)) return
      treeWalkUp(placeForTreeWalkUp.getContext, placeForTreeWalkUp)
    }
    treeWalkUp(place, null) //collecting all references from scope

    val candidates = processor.candidatesS.toSeq
    if (!candidates.isEmpty) return candidates

    processor.clear()

    for (obj <- ScalaPsiUtil.collectImplicitObjects(tp, place)) {
      processor.processType(obj, place, ResolveState.initial())
    }

    processor.candidatesS.toSeq
  }

  class ImplicitParametersProcessor extends ImplicitProcessor(StdKinds.refExprLastRef) {
    def clear() {
      candidatesSet.clear()
    }

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      val subst = getSubst(state)
      named match {
        case o: ScObject if o.hasModifierProperty("implicit") =>
          candidatesSet += new ScalaResolveResult(o, subst, getImports(state))
        case param: ScParameter =>
          if (param.isImplicitParameter)
            candidatesSet += new ScalaResolveResult(param, subst, getImports(state))
        case patt: ScBindingPattern => {
          val memb = ScalaPsiUtil.getContextOfType(patt, true, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") =>
              candidatesSet += new ScalaResolveResult(named, subst, getImports(state))
            case _ =>
          }
        }
        case function: ScFunction if function.hasModifierProperty("implicit") => {
          candidatesSet +=
            new ScalaResolveResult(named, subst.followed(inferMethodTypesArgs(function, subst)), getImports(state))
        }
        case _ =>
      }
      true
    }

    override def candidatesS: scala.collection.Set[ScalaResolveResult] = {
      def forFilter(c: ScalaResolveResult): Option[ScalaResolveResult] = {
        def compute(): Option[ScalaResolveResult] = {
          val subst = c.substitutor
          c.element match {
            case o: ScObject if !PsiTreeUtil.isContextAncestor(o, place, false) =>
              o.getType(TypingContext.empty) match {
                case Success(objType: ScType, _) =>
                  if (!subst.subst(objType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            case param: ScParameter if !PsiTreeUtil.isContextAncestor(param, place, false) =>
              param.getType(TypingContext.empty) match {
                case Success(paramType: ScType, _) =>
                  if (!subst.subst(paramType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            case patt: ScBindingPattern
              if !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(patt), place, false) => {
              patt.getType(TypingContext.empty) match {
                case Success(pattType: ScType, _) =>
                  if (!subst.subst(pattType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            }
            case fun: ScFunction if !PsiTreeUtil.isContextAncestor(fun, place, false) => {
              val oneImplicit = fun.paramClauses.clauses.length == 1 && fun.paramClauses.clauses.apply(0).isImplicit
              fun.getType(TypingContext.empty) match {
                case Success(funType: ScType, _) => {
                  if (subst.subst(funType) conforms tp) {
                    Conformance.undefinedSubst(tp, subst.subst(funType)).getSubstitutor match {
                      case Some(substitutor) =>
                        Some(c.copy(subst.followed(substitutor)))
                      //failed to get implicit parameter, there is no substitution to resolve constraints
                      case None => None
                    }
                  }
                  else {
                    subst.subst(funType) match {
                      case ScFunctionType(ret, params) if params.length == 0 || oneImplicit =>
                        if (!ret.conforms(tp)) None
                        else {
                          Conformance.undefinedSubst(tp, ret).getSubstitutor match {
                            case Some(substitutor) =>
                              Some(c.copy(subst.followed(substitutor)))
                            //failed to get implicit parameter, there is no substitution to resolve constraints
                            case None => None
                          }
                        }
                      case _ => None
                    }
                  }
                }
                case _ => None
              }
            }
            case _ => None
          }
        }

        import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

        doComputations(c.element, (tp: Object, searches: ArrayBuffer[Object]) => searches.find{
          case t: ScType if tp.isInstanceOf[ScType] => t.equiv(tp.asInstanceOf[ScType])
          case _ => false
        } == None,
          tp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
          case Some(res) => res
          case None => None
        }
      }

      val applicable = candidatesSet.map(forFilter).flatten
      new MostSpecificUtil(place, 1).mostSpecificForResolveResult(applicable) match {
        case Some(r) => HashSet(r)
        case _ => applicable
      }
    }


    /**
     Pick all type parameters by method maps them to the appropriate type arguments, if they are
     */
    def inferMethodTypesArgs(fun: ScFunction, classSubst: ScSubstitutor) = {
      fun.typeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)), new ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst), 1))
      }
    }
  }
}