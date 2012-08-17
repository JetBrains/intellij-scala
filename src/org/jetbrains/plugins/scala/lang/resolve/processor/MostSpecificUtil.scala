package org.jetbrains.plugins.scala.lang
package resolve
package processor

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScTypePolymorphicType, ScMethodType, TypeParameter, Parameter}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi._
import scala.collection.Set
import psi.types._
import psi.api.statements._
import psi.api.toplevel.imports.usages.ImportUsed
import psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.extensions.toPsiMemberExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.04.2010
 */

case class MostSpecificUtil(elem: PsiElement, length: Int) {
  def mostSpecificForResolveResult(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] = {
    mostSpecificGeneric(applicable.map(r => r.innerResolveResult match {
      case Some(rr) => new InnerScalaResolveResult(rr.element, rr.implicitConversionClass, r, r.substitutor)
      case None => new InnerScalaResolveResult(r.element, r.implicitConversionClass, r, r.substitutor)
    })).map(_.repr)
  }

  def mostSpecificForImplicitParameters(applicable: Set[(ScalaResolveResult, ScSubstitutor)]): Option[ScalaResolveResult] = {
    mostSpecificGeneric(applicable.map{case (r, subst) => r.innerResolveResult match {
      case Some(rr) => new InnerScalaResolveResult(rr.element, rr.implicitConversionClass, r, subst)
      case None => new InnerScalaResolveResult(r.element, r.implicitConversionClass, r, subst)
    }}).map(_.repr)
  }

  def mostSpecificForImplicit(applicable: Set[(ScType, PsiNamedElement, Set[ImportUsed], ScSubstitutor)]):
    Option[(ScType, PsiNamedElement, Set[ImportUsed], ScSubstitutor)] = {
    mostSpecificGeneric(applicable.map(r => {
      var callByName = false
      r._2 match {
        case f: ScFunction =>
          val clauses = f.paramClauses.clauses
          if (clauses.length > 0 && clauses(0).parameters.length == 1 && clauses(0).parameters(0).isCallByNameParameter) {
            callByName = true
          }
        case _ =>
      }
      new InnerScalaResolveResult(r._2, None, r, r._4, callByName)
    })).map(_.repr)
  }

  private class InnerScalaResolveResult[T](val element: PsiNamedElement, val implicitConversionClass: Option[PsiClass],
                                           val repr: T, val substitutor: ScSubstitutor,
                                           val callByNameImplicit: Boolean = false)

  private def isAsSpecificAs[T](r1: InnerScalaResolveResult[T], r2: InnerScalaResolveResult[T]): Boolean = {
    def lastRepeated(params: Seq[Parameter]): Boolean = {
      val lastOption: Option[Parameter] = params.lastOption
      if (lastOption == None) return false
      lastOption.get.isRepeated
    }
    (r1.element, r2.element) match {
      case (m1@(_: PsiMethod | _: ScFun), m2@(_: PsiMethod | _: ScFun)) => {
        val (t1, t2) = (r1.substitutor.subst(getType(m1)), r2.substitutor.subst(getType(m2)))
        val (t11, t22) = (getType(m1), getType(m2))
        def foo(t1: ScType, t2: ScType): Boolean = {
          def calcParams(tp: ScType, existential: Boolean): Seq[Parameter] = {
            tp match {
              case ScMethodType(_, params, _) => params
              case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) => {
                if (!existential) {
                  val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                    (subst: ScSubstitutor, tp: TypeParameter) =>
                      subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
                        new ScUndefinedType(ScalaPsiManager.typeVariable(tp.ptp)))
                  }
                  params.map(p => p.copy(paramType = s.subst(p.paramType)))
                } else {
                  val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                    (subst: ScSubstitutor, tp: TypeParameter) =>
                      subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
                        new ScTypeVariable(tp.name))
                  }
                  val arguments = typeParams.toList.map(tp =>
                    new ScExistentialArgument(tp.name, List.empty /* todo? */ , s.subst(tp.lowerType), s.subst(tp.upperType)))
                  params.map(p => p.copy(paramType = ScExistentialType(s.subst(p.paramType), arguments)))
                }
              }
              case _ => Seq.empty
            }
          }
          var params1 = calcParams(t1, true)
          val params2 = calcParams(t2, false)
          if (((t1.isInstanceOf[ScTypePolymorphicType] && t2.isInstanceOf[ScTypePolymorphicType]) ||
            (!(m1.isInstanceOf[ScFunction] || m1.isInstanceOf[ScFun]) || !(m2.isInstanceOf[ScFunction] || m2.isInstanceOf[ScFun]))) &&
            (lastRepeated(params1) ^ lastRepeated(params2))) return lastRepeated(params2) //todo: this is hack!!! see SCL-3846, SCL-4048
          if (lastRepeated(params1) && !lastRepeated(params2)) params1 = params1.map {
            case p: Parameter if p.isRepeated =>
              val seq = ScalaPsiManager.instance(r1.element.getProject).getCachedClass(r1.element.getResolveScope,
                "scala.collection.Seq")
              if (seq != null) {
                val newParamType = p.paramType match {
                  case ScExistentialType(q, wilds) =>
                    ScExistentialType(ScParameterizedType(ScDesignatorType(seq), Seq(q)), wilds)
                  case paramType => ScParameterizedType(ScDesignatorType(seq), Seq(paramType))
                }
                Parameter(p.name, newParamType, p.expectedType,
                  p.isDefault, false, p.isByName)
              }
              else p
            case p => p
          }
          val i: Int = if (params1.length > 0) 0.max(length - params1.length) else 0
          val default: Expression = new Expression(if (params1.length > 0) params1.last.paramType else Nothing)
          val exprs: Seq[Expression] = params1.map(p => new Expression(p.paramType)) ++ Seq.fill(i)(default)
          val conformance = Compatibility.checkConformance(false, params2, exprs, false)
          var u = conformance._2
          if (!conformance._1) return false
          t2 match {
            case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) => {
              u.getSubstitutor match {
                case Some(uSubst) =>
                  typeParams.foreach(tp => {
                    u = u.addLower((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), uSubst.subst(tp.lowerType))
                    u = u.addUpper((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), uSubst.subst(tp.upperType))
                  })
                case None => return false
              }
            }
            case _ =>
          }
          u.getSubstitutor match {
            case None => false
            case _ => true
          }
        }

        val res1 = foo(t1, t2)
        val res2 = foo(t11, t22)
        if (res1 != res2) {
          "stop"
        }
        res1
      }
      case (_, m2: PsiMethod) => true
      case (e1, e2) => Compatibility.compatibleWithViewApplicability(getType(e1), getType(e2))
    }
  }

  private def getClazz[T](r: InnerScalaResolveResult[T]): Option[PsiClass] = {
    val element = ScalaPsiUtil.nameContext(r.element)
    element match {
      case memb: PsiMember => {
        val clazz = memb.containingClass
        if (clazz == null) None else Some(clazz)
      }
      case _ => None
    }
  }

  def isDerived(c1: Option[PsiClass], c2: Option[PsiClass]): Boolean = {
    (c1, c2) match {
      case (Some(c1), Some(c2)) => {
        if (c1 == c2) return false
        if (ScalaPsiUtil.cachedDeepIsInheritor(c1, c2)) return true
        ScalaPsiUtil.getCompanionModule(c1) match {
          case Some(c1) => if (ScalaPsiUtil.cachedDeepIsInheritor(c1, c2)) return true
          case _ =>
        }
        ScalaPsiUtil.getCompanionModule(c2) match {
          case Some(c2) => if (ScalaPsiUtil.cachedDeepIsInheritor(c1, c2)) return true
          case _ =>
        }
        (ScalaPsiUtil.getCompanionModule(c1), ScalaPsiUtil.getCompanionModule(c2)) match {
          case (Some(c1), Some(c2)) => if (ScalaPsiUtil.cachedDeepIsInheritor(c1, c2)) return true
          case _ =>
        }
        false
      }
      case _ => false
    }
  }

  private def relativeWeight[T](r1: InnerScalaResolveResult[T], r2: InnerScalaResolveResult[T]): Int = {
    val s1 = if (isAsSpecificAs(r1, r2)) 1 else 0
    val s2 = if (isDerived(getClazz(r1), getClazz(r2))) 1 else 0
    s1 + s2
  }

  private def isMoreSpecific[T](r1: InnerScalaResolveResult[T], r2: InnerScalaResolveResult[T]): Boolean = {
    (r1.implicitConversionClass, r2.implicitConversionClass) match {
      case (Some(t1), Some(t2)) => if (ScalaPsiUtil.cachedDeepIsInheritor(t1, t2)) return true
      case _ =>
    }
    if (r1.callByNameImplicit ^ r2.callByNameImplicit) return !r1.callByNameImplicit
    val weightR1R2 = relativeWeight(r1, r2)
    val weightR2R1 = relativeWeight(r2, r1)
    weightR1R2 > weightR2R1
  }

  private def mostSpecificGeneric[T](applicable: Set[InnerScalaResolveResult[T]]): Option[InnerScalaResolveResult[T]] = {
    val a1iterator = applicable.iterator
    while (a1iterator.hasNext) {
      val a1 = a1iterator.next()
      var break = false
      val a2iterator = applicable.iterator
      while (a2iterator.hasNext && !break) {
        val a2 = a2iterator.next()
        if (a1 != a2 && !isMoreSpecific(a1, a2)) break = true
      }
      if (!break) return Some(a1)
    }
    None
  }

  //todo: implement existential dual
  def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => fun.polymorphicType
    case f: ScFunction => f.polymorphicType
    case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, ScSubstitutor.empty, elem.getResolveScope)
    case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
      case pd: ScPatternDefinition if (PsiTreeUtil.isContextAncestor(pd, elem, true)) =>
        pd.declaredType match {case Some(t) => t; case None => Nothing}
      case vd: ScVariableDefinition if (PsiTreeUtil.isContextAncestor(vd, elem, true)) =>
        vd.declaredType match {case Some(t) => t; case None => Nothing}
      case _ => refPatt.getType(TypingContext.empty).getOrAny
    }
    case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrAny
    case _ => Nothing
  }
}