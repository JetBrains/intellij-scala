package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.{HierarchicalMethodSignature, PsiClass, PsiManager, PsiMethod}
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import java.util
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod

/**
 * @author Alefas
 * @since 05.04.12
 */

trait LightScalaMethod

class LightMethodAdapter(manager: PsiManager, method: PsiMethod, containingClass: PsiClass) extends
  LightMethod(manager, method, containingClass) {

  override def findDeepestSuperMethods(): Array[PsiMethod] = PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

  override def findDeepestSuperMethod(): PsiMethod = PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

  override def findSuperMethods(): Array[PsiMethod] = PsiSuperMethodImplUtil.findSuperMethods(this)

  override def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess)

  override def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiSuperMethodImplUtil.findSuperMethods(this, parentClass)

  override def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): util.List[MethodSignatureBackedByPsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

  override def getHierarchicalMethodSignature: HierarchicalMethodSignature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)
}