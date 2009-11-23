package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition, ScTemplateDefinition}
import result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import collection.mutable.ArrayBuffer
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScBindingPattern}
import util.PsiTreeUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */

class ImplicitParametersCollector(place: PsiElement, tp: ScType) {
  def collectImplicitClasses(tp: ScType): Seq[(PsiClass, ScSubstitutor)] = {
    tp match {
      case ScCompoundType(comps, _, _) => {
        comps.flatMap(collectImplicitClasses(_))
      }
      case p@ScParameterizedType(des, args) => {
        (ScType.extractClassType(p) match {
          case Some(pair) => Seq(pair)
          case _ => Seq.empty
        }) ++ args.flatMap(collectImplicitClasses(_))
      }
      case singl@ScSingletonType(path) => collectImplicitClasses(singl.pathType)
      case _=> {
        ScType.extractClassType(tp) match {
          case Some(pair) => Seq(pair)
          case _ => Seq.empty
        }
      }
    }
  }

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
    for ((clazz, subst) <- collectImplicitClasses(tp)) {
      clazz.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, subst), null, place)
      clazz match {
        case td: ScTemplateDefinition => ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) => {
            td.processDeclarations(processor, ResolveState.initial, null, place)
          }
          case _ =>
        }
        case _ =>
      }
    }

    return processor.candidates.toSeq
  }

  class ImplicitParametersProcessor extends BaseProcessor(StdKinds.methodRef) {
    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      val subst = getSubst(state)
      named match {
        case patt: ScBindingPattern => {
          val memb = ScalaPsiUtil.getParentOfType(patt, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") => candidatesSet += new ScalaResolveResult(named, subst)
            case _ =>
          }
        }
        case function: ScFunction if function.hasModifierProperty("implicit") => {
          candidatesSet += new ScalaResolveResult(named, subst.followed(inferMethodTypesArgs(function, subst)))
        }
        case _ =>
      }
      true
    }

    override def candidates[T >: ScalaResolveResult: ClassManifest]: Array[T] = {
      def forFilter(c: ScalaResolveResult): Boolean = {
        val subst = c.substitutor
        c.element match {
          case patt: ScBindingPattern => {
            patt.getType(TypingContext.empty) match {
              case Success(pattType: ScType, _) => subst.subst(pattType) conforms tp
              case _ => false
            }
          }
          case fun: ScFunction => {
            val oneImplicit = fun.paramClauses.clauses.length == 1 && fun.paramClauses.clauses.apply(0).isImplicit
            fun.getType(TypingContext.empty) match {
              case Success(funType: ScType, _) => {
                if (subst.subst(funType) conforms tp) return true
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
      def forMap(c: ScalaResolveResult): ScalaResolveResult = {
        val subst = c.substitutor
        c.element match {
          case fun: ScFunction if fun.typeParameters.length > 0 => {
            val funType = fun.getType(TypingContext.empty).get
            val undefSubst = {
              if (subst.subst(funType) conforms tp) Conformance.undefinedSubst(tp, subst.subst(funType))
              else {
                subst.subst(funType) match {
                  case ScFunctionType(ret, params) => Conformance.undefinedSubst(tp, ret)
                }
              }
            }
            undefSubst.getSubstitutor match {
              case Some(s: ScSubstitutor) => new ScalaResolveResult(fun, subst.followed(s), c.importsUsed,
                c.nameShadow, c.implicitConversionClass)
              case _ => c
            }
          }
          case _ => c
        }
      }
      val applicable: Array[ScalaResolveResult] = candidatesSet.toArray.filter(forFilter(_)).map(forMap(_))
      if (applicable.length <= 1) applicable.toArray
      else {
        val buff = new ArrayBuffer[ScalaResolveResult]
        def existsBetter(r: ScalaResolveResult): Boolean = {
          for (r1 <- applicable if r != r1) {
            if (isMoreSpecific(r1.element, r.element)) return true
          }
          false
        }
        for (r <- applicable if !existsBetter(r)) {
          buff += r
        }
        buff.toArray[T]
      }
    }


    //todo: not sure about this copy works right: needs more think
    def isMoreSpecific(e1: PsiNamedElement, e2: PsiNamedElement): Boolean = {
      val a1 = isAsSpecificAs(e1, e2)
      val a2 = isAsSpecificAs(e2, e1)
      (a1, a2) match {
        case (true, false) => true
        case (false, true) => false
        case _ if a1 == a2 => {
          val e1td = PsiTreeUtil.getParentOfType(e1, classOf[ScTemplateDefinition])
          val e2td = PsiTreeUtil.getParentOfType(e2, classOf[ScTemplateDefinition])
          if (e1td == null && e2td != null) return true
          if (e2td == null) return false
          e1td.isInheritor(e2td, true)
        }
      }
    }

    def isAsSpecificAs(e1: PsiNamedElement, e2: PsiNamedElement): Boolean = {
      if (Compatibility.compatible(getType(e1), getType(e2))) return true
      if (e2.isInstanceOf[ScFunction] && !e1.isInstanceOf[ScFunction]) return true
      return false
    }

    private def getType(e: PsiNamedElement): ScType = e match {
      case fun: ScFun => new ScFunctionType(fun.retType, collection.immutable.Seq(fun.paramTypes.toSeq: _*))
      case f: ScFunction => {
        val p = if (PsiTreeUtil.isAncestor(f, place, true))
          new ScFunctionType(f.declaredType.getOrElse(Any), collection.immutable.Seq(f.paramTypes.toSeq: _*))
        else f.getType(TypingContext.empty).getOrElse(Any)
        if (f.parameters.length == 0 || f.paramClauses.clauses.apply(0).isImplicit) {
          p match {
            case ScFunctionType(ret, _) => ret
            case _ => p
          }
        } else p
      }
      case m: PsiMethod => ResolveUtils.methodType(m, ScSubstitutor.empty)

      case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
        case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, place, true)) =>
          pd.declaredType match {case Some(t) => t; case None => Nothing}
        case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, place, true)) =>
          vd.declaredType match {case Some(t) => t; case None => Nothing}
        case _ => refPatt.getType(TypingContext.empty).getOrElse(Any)
      }

      case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrElse(Any)
      case _ => Nothing
    }

    /**
     Pick all type parameters by method maps them to the appropriate type arguments, if they are
     */
    def inferMethodTypesArgs(fun: ScFunction, classSubst: ScSubstitutor) = {
      fun.typeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst), 1))
      }
    }
  }
}

