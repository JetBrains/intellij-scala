package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.PsiClass
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.lang.psi.ScalaInheritorsFinder

import scala.jdk.CollectionConverters.CollectionHasAsScala

/** [[ScalaInheritorsFinder]] from [[ClassInheritorsSearch]] */
final class SearchScalaInheritorsFinder extends ScalaInheritorsFinder {

  override def inheritorsInFile(clazz: PsiClass): Iterable[PsiClass] =
    ClassInheritorsSearch.search(clazz, new LocalSearchScope(clazz.getContainingFile), true).findAll().asScala
}
