package org.jetbrains.plugins.scala.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScTypeExt}

object ScEquivalenceUtil {
  def areClassesEquivalent(clazz1: PsiClass, clazz2: PsiClass): Boolean = {
    if (clazz1 == clazz2)
      return true

    if (clazz1 == null && clazz2 != null || clazz1 != null && clazz2 == null)
      return false

    if (clazz1.name != clazz2.name)
      return false

    val containingClazz1: PsiClass = clazz1.containingClass
    val containingClass2: PsiClass = clazz2.containingClass

    if (!areClassesEquivalent(containingClazz1, containingClass2))
      return false

    if (clazz1.qualifiedName != clazz2.qualifiedName)
      return false

    if (isLocalOrAnonymous(clazz1) || isLocalOrAnonymous(clazz2))
      return false

    clazz1.isInstanceOf[ScObject] == clazz2.isInstanceOf[ScObject]
  }

  private def isLocalOrAnonymous(clazz: PsiClass) =
    clazz.qualifiedName == null || PsiTreeUtil.getContextOfType(clazz, true, classOf[PsiClass]) != null && clazz.getContainingClass == null

  def arePackagesEquivalent(p1: PsiPackage, p2: PsiPackage): Boolean = {
    p1 != null && p2 != null &&
      p1.getManager == p2.getManager &&
      p1.getQualifiedName == p2.getQualifiedName
  }

  private def areTypeAliasesEquivalent(ta1: ScTypeAlias, ta2: ScTypeAlias): Boolean = {
    def equiv(tr1: TypeResult, tr2: TypeResult): Boolean =
      (tr1, tr2) match {
        case (Right(left), Right(right)) => left.equiv(right)
        case _ => false
    }

    if (ta1.isExistentialTypeAlias && ta2.isExistentialTypeAlias) {
      equiv(ta1.lowerBound, ta2.lowerBound) && equiv(ta1.upperBound, ta2.upperBound)
    }
    else ta1 == ta2
  }

  def smartEquivalence(elem1: PsiElement, elem2: PsiElement): Boolean =
    (elem1, elem2) match {
      case (clazz1: PsiClass, clazz2: PsiClass) => areClassesEquivalent(clazz1, clazz2)
      case (p1: PsiPackage, p2: PsiPackage)     => arePackagesEquivalent(p1, p2)
      case (ta1: ScTypeAlias, ta2: ScTypeAlias) => areTypeAliasesEquivalent(ta1, ta2)
      case _                                    => elem1 == elem2
    }

  /**
   * Checks if provided [[DesignatorOwner]] references class/type alias
   * and so is equivalent to it's "eta expansion" poly type in a higher-kinded
   * scenario. e.g. `F[Option]` and `F[[A] Option[A]]`.
   */
  def isDesignatorEqiuivalentToPolyType(
    des:         DesignatorOwner,
    poly:        ScTypePolymorphicType,
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  ): Option[ConstraintsResult] = des.polyTypeOption.map(_.equiv(poly, constraints, falseUndef))
}