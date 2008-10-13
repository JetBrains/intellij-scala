package org.jetbrains.plugins.scala.finder

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import _root_.scala.collection.mutable.ArrayBuffer
import caches.ScalaCachesManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElementFinder, PsiClass, PsiPackage}
import lang.psi.api.ScalaFile

class ScalaClassFinder(project: Project) extends ProjectComponent with PsiElementFinder {
  def projectOpened {}
  def projectClosed {}
  def getComponentName = "Scala Class Finder"
  def initComponent {}
  def disposeComponent {}

  def findClass(qName: String, scope: GlobalSearchScope) =
    project.getComponent(classOf[ScalaCachesManager]).getNamesCache.getClassByFQName(qName, scope)

  def findClasses(qName: String, scope: GlobalSearchScope) =
    project.getComponent(classOf[ScalaCachesManager]).getNamesCache.getClassesByFQName(qName, scope)

  def findPackage(qName: String): PsiPackage = ScalaPsiManager.instance(project).syntheticPackage(qName)
  
  def getSubPackages(p: PsiPackage, scope: GlobalSearchScope) = Array[PsiPackage]() //todo

  def getClasses(p: PsiPackage, scope: GlobalSearchScope) = p match {
    case synth: ScSyntheticPackage => Array[PsiClass]() //todo
    case _ => {
      val buff = new ArrayBuffer[PsiClass]
      for (dir <- p.getDirectories(scope); file <- dir.getFiles) file match {
        case scala: ScalaFile => buff ++= scala.typeDefinitions
        case _ =>
      }
      buff.toArray
    }
  }
}