package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.ScalaInheritorsFinder

implicit class PsiClassInheritorsExt(private val clazz: PsiClass) extends AnyVal {
  def directInheritorsOfSealed: Iterable[PsiClass] =
    if (!clazz.isSealed) Iterable.empty
    else ScalaInheritorsFinder.instance.inheritorsInFile(clazz)
}
