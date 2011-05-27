package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import processor.{MostSpecificUtil, BaseProcessor}
import result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import collection.immutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitParametersCollector(place: PsiElement, tp: ScType) {
  def collect: Seq[ScalaResolveResult] = {
    val processor = new ImplicitParametersProcessor
    def treeWalkUp(placeForTreeWalkUp: PsiElement, lastParent: PsiElement) {
      placeForTreeWalkUp match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial(),
            lastParent, place)) return
          treeWalkUp(placeForTreeWalkUp.getContext, placeForTreeWalkUp)
        }
      }
    }
    treeWalkUp(place, null) //collecting all references from scope
    //todo: check for candidates, if not found, continue
    for (obj <- ScalaPsiUtil.collectImplicitObjects(tp, place)) {
      obj.processDeclarations(processor, ResolveState.initial, null, place)
    }

    processor.candidatesS.toSeq
  }

  class ImplicitParametersProcessor extends BaseProcessor(StdKinds.methodRef) {
    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      val subst = getSubst(state)
      named match {
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
      def forFilter(c: ScalaResolveResult): Boolean = {
        def compute(): Boolean = {
          val subst = c.substitutor
          c.element match {
            case patt: ScBindingPattern
              if !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(patt), place, false)=> {
              patt.getType(TypingContext.empty) match {
                case Success(pattType: ScType, _) => subst.subst(pattType) conforms tp
                case _ => false
              }
            }
            case fun: ScFunction if !PsiTreeUtil.isContextAncestor(fun, place, false) => {
              val oneImplicit = fun.paramClauses.clauses.length == 1 && fun.paramClauses.clauses.apply(0).isImplicit
              fun.getType(TypingContext.empty) match {
                case Success(funType: ScType, _) => {
                  if (subst.subst(funType) conforms tp) true
                  else {
                    subst.subst(funType) match {
                      case ScFunctionType(ret, params) if params.length == 0 || oneImplicit => ret conforms tp
                      case _ => false
                    }
                  }
                }
                case _ => false
              }
            }
            case _ => false
          }
        }

        import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

        doComputations(c.element, (tp: ScType, searches: List[ScType]) => searches.find(_.equiv(tp)) == None,
          tp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
          case Some(res) => res
          case None => false
        }
      }
      def forMap(c: ScalaResolveResult): ScalaResolveResult = {
        def compute(): ScalaResolveResult = {
          val subst = c.substitutor
          c.element match {
            case fun: ScFunction if fun.typeParameters.length > 0 => {
              val funType = fun.getType(TypingContext.empty).get
              val undefSubst = {
                if (subst.subst(funType) conforms tp) Conformance.undefinedSubst(tp, subst.subst(funType))
                else {
                  subst.subst(funType) match {
                    case ScFunctionType(ret, params) =>
                      Conformance.undefinedSubst(tp, ret) //todo: check is implicit first parameter clause
                  }
                }
              }
              undefSubst.getSubstitutor match {
                case Some(s: ScSubstitutor) => c.copy(subst.followed(s))
                case _ => c
              }
            }
            case _ => c
          }
        }

        import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

        (doComputations(c.element, (tp: ScType, searches: List[ScType]) => true,
          tp, compute() , IMPLICIT_PARAM_TYPES_KEY): @unchecked) match {
          case Some(res) => res
        }
      }
      val applicable = candidatesSet.filter(forFilter(_)).map(forMap(_))
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