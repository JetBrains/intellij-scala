package org.jetbrains.plugins.scala.util

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{toPsiMemberExt, toPsiNamedElementExt, toPsiClassExt}

/**
 * @author Alexander Podkhalyuzin
 */
object ScEquivalenceUtil {
  def areClassesEquivalent(clazz1: PsiClass, clazz2: PsiClass): Boolean = {
    if (clazz1.name != clazz2.name) return false
    if (clazz1 == clazz2) return true
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
      PsiTreeUtil.getContextOfType(clazz1, true, classOf[PsiClass]) != null ||
      PsiTreeUtil.getContextOfType(clazz2, true, classOf[PsiClass]) != null
    
    if (isSomeClassLocalOrAnonymous) return false
     
    clazz1 match {
      case _: ScObject => clazz2.isInstanceOf[ScObject]
      case _ => !clazz2.isInstanceOf[ScObject]
    }
  }

  def smartEquivalence(elem1: PsiElement, elem2: PsiElement): Boolean = {
    (elem1, elem2) match {
      case (clazz1: PsiClass, clazz2: PsiClass) => areClassesEquivalent(clazz1, clazz2)
      case _ => elem1 == elem2
    }
  }
}