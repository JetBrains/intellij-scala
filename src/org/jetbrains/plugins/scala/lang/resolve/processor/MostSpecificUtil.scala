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

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.04.2010
 */

case class MostSpecificUtil(elem: PsiElement, length: Int) {
  def mostSpecificForResolveResult(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] = {
    mostSpecificGeneric(applicable.map(r => InnerScalaResolveResult(r.element, r.implicitConversionClass, r))).map(_.repr)
  }

  def mostSpecificForImplicit(applicable: Set[(ScType, ScFunctionDefinition, Set[ImportUsed])]): Option[(ScType, ScFunctionDefinition, Set[ImportUsed])] = {
    mostSpecificGeneric(applicable.map(r => InnerScalaResolveResult(r._2, None, r))).map(_.repr)
  }

  def mostSpecificForPsiMethod(applicable: Set[PsiMethod]): Option[PsiMethod] = {
    mostSpecificGeneric(applicable.map(r => InnerScalaResolveResult(r, None, r))).map(_.repr)
  }

  private case class InnerScalaResolveResult[T](element: PsiNamedElement, implicitConversionClass: Option[PsiClass],
                                                repr: T)

  private def isAsSpecificAs[T](r1: InnerScalaResolveResult[T], r2: InnerScalaResolveResult[T]): Boolean = {
    def lastRepeated(params: Seq[Parameter]): Boolean = {
      params.lastOption.getOrElse(return false).isRepeated
    }
    (r1.element, r2.element) match {
      case (m1@(_: PsiMethod | _: ScFun), m2@(_: PsiMethod | _: ScFun)) => {
        val (t1, t2) = (getType(m1), getType(m2))
        def calcParams(tp: ScType): Seq[Parameter] = {
          tp match {
            case ScMethodType(_, params, _) => params
            case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) => {
              val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                (subst: ScSubstitutor, tp: TypeParameter) =>
                  subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), new ScExistentialArgument(tp.name, List.empty, tp.lowerType, tp.upperType))
              }
              params.map(p => Parameter(p.name, s.subst(p.paramType), p.isDefault, p.isRepeated))
            }
            case _ => Seq.empty
          }
        }
        val (params1, params2) = (calcParams(t1), calcParams(t2))
        if (lastRepeated(params1) && !lastRepeated(params2)) return false
        val i: Int = if (params1.length > 0) 0.max(length - params1.length) else 0
        val default: Expression = new Expression(if (params1.length > 0) params1.last.paramType else Nothing)
        val exprs: Seq[Expression] = params1.map(p => new Expression(p.paramType)) ++ Seq.fill(i)(default)
        return Compatibility.checkConformance(false, params2, exprs, false)._1
      }
      case (_, m2: PsiMethod) => return true
      case (e1, e2) => return Compatibility.compatibleWithViewApplicability(getType(e1), getType(e2))
    }
  }

  private def getClazz[T](r: InnerScalaResolveResult[T]): Option[PsiClass] = {
    val element = ScalaPsiUtil.nameContext(r.element)
    element match {
      case memb: PsiMember => {
        val clazz = memb.getContainingClass
        if (clazz == null) None else Some(clazz)
      }
      case _ => None
    }
  }

  def isDerived(c1: Option[PsiClass], c2: Option[PsiClass]): Boolean = {
    (c1, c2) match {
      case (Some(c1), Some(c2)) => {
        if (c1 == c2) return false
        if (c1.isInheritor(c2, true)) return true
        ScalaPsiUtil.getCompanionModule(c1) match {
          case Some(c1) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        ScalaPsiUtil.getCompanionModule(c2) match {
          case Some(c2) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        return false
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
      case (Some(t1), Some(t2)) => if (t1.isInheritor(t2, true)) return true
      case _ =>
    }
    relativeWeight(r1, r2) > relativeWeight(r2, r1)
  }

  private def mostSpecificGeneric[T](applicable: Set[InnerScalaResolveResult[T]]): Option[InnerScalaResolveResult[T]] = {
    for (a1 <- applicable) {
      var break = false
      for (a2 <- applicable if a1 != a2 && !break) {
        if (!isMoreSpecific(a1, a2)) break = true
      }
      if (!break) return Some(a1)
    }
    return None
  }

  //todo: implement existential dual
  def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => fun.polymorphicType
    case f: ScFunction => f.polymorphicType
    case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, ScSubstitutor.empty, elem.getResolveScope)
    case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
      case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, elem, true)) =>
        pd.declaredType match {case Some(t) => t; case None => Nothing}
      case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, elem, true)) =>
        vd.declaredType match {case Some(t) => t; case None => Nothing}
      case _ => refPatt.getType(TypingContext.empty).getOrElse(Any)
    }
    case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrElse(Any)
    case _ => Nothing
  }
}