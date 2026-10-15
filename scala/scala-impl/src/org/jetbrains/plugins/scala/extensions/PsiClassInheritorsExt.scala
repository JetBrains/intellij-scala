package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiClass
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch

import scala.jdk.CollectionConverters.*

implicit class PsiClassInheritorsExt(private val clazz: PsiClass) extends AnyVal {
  def directInheritorsOfSealed: Iterable[PsiClass] =
    if (!clazz.isSealed) Iterable.empty
    else
      ClassInheritorsSearch.search(
        clazz,
        new LocalSearchScope(clazz.getContainingFile),
        true
      ).findAll().asScala
}
