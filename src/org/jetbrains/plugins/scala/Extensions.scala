package org.jetbrains.plugins.scala

import com.intellij.psi.PsiMethod
import extensions.PsiMethodExt

/**
 * Pavel Fatin
 */

object Extensions {
  implicit def toPsiMethodExt(method: PsiMethod) = new PsiMethodExt(method)
}