package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.PsiClass
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.lang.psi.ScalaInheritorsFinder
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors

import scala.jdk.CollectionConverters.CollectionHasAsScala

/** [[ScalaInheritorsFinder]] from [[ClassInheritorsSearch]] and [[ScalaInheritors]] */
final class SearchScalaInheritorsFinder extends ScalaInheritorsFinder {

  override def inheritorsInFile(clazz: PsiClass): Iterable[PsiClass] =
    ClassInheritorsSearch.search(clazz, new LocalSearchScope(clazz.getContainingFile), true).findAll().asScala

  override def allInheritors(clazz: PsiClass): Set[PsiClass] = ScalaInheritors.withAllInheritors(clazz)

  override def inheritors(clazz: PsiClass, scope: GlobalSearchScope): Iterable[PsiClass] =
    ClassInheritorsSearch.search(clazz, scope, true).findAll().asScala
}
