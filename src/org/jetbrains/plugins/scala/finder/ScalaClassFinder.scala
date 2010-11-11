package org.jetbrains.plugins.scala
package finder

import caches.ScalaCachesManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi._
import lang.psi.impl.ScalaPsiManager
import java.lang.String
import java.util.Set

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    Array.empty
  }

  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    null
  }

  override def findPackage(qName: String): PsiPackage =
    ScalaPsiManager.instance(project).syntheticPackage(qName)

  override def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    ScalaCachesManager.getInstance(project).getNamesCache.getClassNames(psiPackage, scope)
  }
}