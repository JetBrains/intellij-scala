package org.jetbrains.plugins.scala
package finder

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import _root_.scala.collection.mutable.ArrayBuffer
import caches.ScalaCachesManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElementFinder, PsiClass, PsiPackage}
import lang.psi.api.ScalaFile

class ScalaClassFinder(project: Project) extends PsiElementFinder {

  def findClass(qName: String, scope: GlobalSearchScope) =
    ScalaCachesManager.getInstance(project).getNamesCache.getClassByFQName(qName, scope)

  def findClasses(qName: String, scope: GlobalSearchScope) =
    ScalaCachesManager.getInstance(project).getNamesCache.getClassesByFQName(qName, scope).filter {
      c => c.getContainingFile match {
        case s : ScalaFile => !s.isScriptFile
        case _ => true
      }
    }

  override def findPackage(qName: String): PsiPackage = ScalaPsiManager.instance(project).syntheticPackage(qName)
  
  override def getSubPackages(p: PsiPackage, scope: GlobalSearchScope) = Array[PsiPackage]() //todo

  override def getClasses(p: PsiPackage, scope: GlobalSearchScope) = p match {
    case synth: ScSyntheticPackage => Array[PsiClass]() //todo
    case _ => Array[PsiClass]()
  }
}