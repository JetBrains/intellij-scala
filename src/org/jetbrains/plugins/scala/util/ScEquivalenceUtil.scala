package org.jetbrains.plugins.scala.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * @author Alexander Podkhalyuzin
 */
object ScEquivalenceUtil {
  def areClassesEquivalent(clazz1: PsiClass, clazz2: PsiClass): Boolean = {
    if (clazz1 == clazz2) return true
    if (clazz1.name != clazz2.name) return false
    val containingClazz1: PsiClass = clazz1.containingClass
    val containingClass2: PsiClass = clazz2.containingClass
    if (containingClazz1 != null) {
      if (containingClass2 != null) {
        if (!areClassesEquivalent(containingClazz1, containingClass2)) return false
      }
      else return false
    } else if (containingClass2 != null) return false
    if (clazz1.qualifiedName != clazz2.qualifiedName) return false
    val isSomeClassLocalOrAnonymous = clazz1.qualifiedName == null || clazz2.qualifiedName == null ||
      (PsiTreeUtil.getContextOfType(clazz1, true, classOf[PsiClass]) != null && clazz1.getContainingClass == null) ||
      (PsiTreeUtil.getContextOfType(clazz2, true, classOf[PsiClass]) != null && clazz2.getContainingClass == null)
    
    if (isSomeClassLocalOrAnonymous) return false
     
    clazz1 match {
      case _: ScObject => clazz2.isInstanceOf[ScObject]
      case _ => !clazz2.isInstanceOf[ScObject]
    }
  }

  def arePackagesEquivalent(p1: PsiPackage, p2: PsiPackage) = {
    p1 != null && p2 != null &&
      p1.getManager == p2.getManager &&
      p1.getQualifiedName == p2.getQualifiedName
  }

  private def areTypeAliasesEquivalent(ta1: ScTypeAlias, ta2: ScTypeAlias): Boolean = {
    def equiv(tr1: TypeResult[ScType], tr2: TypeResult[ScType]): Boolean = {
      if (tr1.isEmpty || tr2.isEmpty) false
      else tr1.get.equiv(tr2.get)(ta1.getProject.typeSystem)
    }

    if (ta1.isExistentialTypeAlias && ta2.isExistentialTypeAlias) {
      equiv(ta1.lowerBound, ta2.lowerBound) && equiv(ta1.upperBound, ta2.upperBound)
    }
    else ta1 == ta2
  }

  def smartEquivalence(elem1: PsiElement, elem2: PsiElement): Boolean = {
    (elem1, elem2) match {
      case (clazz1: PsiClass, clazz2: PsiClass) => areClassesEquivalent(clazz1, clazz2)
      case (p1: PsiPackage, p2: PsiPackage) => arePackagesEquivalent(p1, p2)
      case (ta1: ScTypeAlias, ta2: ScTypeAlias) => areTypeAliasesEquivalent(ta1, ta2)
      case _ => elem1 == elem2
    }
  }
}