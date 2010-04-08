package org.jetbrains.plugins.scala
package finder

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import _root_.scala.collection.mutable.ArrayBuffer
import caches.ScalaCachesManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import lang.psi.api.ScalaFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.{ProjectRootManager, ProjectFileIndex}
import com.intellij.openapi.fileTypes.{StdFileTypes, FileType}
import com.intellij.openapi.module.Module
import com.intellij.psi._
import java.util.ArrayList
import com.intellij.openapi.util.Comparing
import lang.psi.api.toplevel.typedef.ScTypeDefinition

class ScalaClassFinder(project: Project) extends PsiElementFinder {

  def findClass(qName: String, scope: GlobalSearchScope) =
    ScalaCachesManager.getInstance(project).getNamesCache.getClassByFQName(qName, scope)

  def findClasses(qName: String, scope: GlobalSearchScope) =
    ScalaCachesManager.getInstance(project).getNamesCache.getClassesByFQName(qName, scope).filter {
      c => c.getContainingFile match {
        case s : ScalaFile => !s.isScriptFile()
        case _ => true
      }
    }

  override def findPackage(qName: String): PsiPackage = ScalaPsiManager.instance(project).syntheticPackage(qName)
  
  override def getSubPackages(p: PsiPackage, scope: GlobalSearchScope) = Array[PsiPackage]() //todo

  override def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    //todo: synthetic packages? it's PsiElementFinderImpl implementation copy
    var buffer: ArrayBuffer[PsiClass] = new ArrayBuffer
    val dirs: Array[PsiDirectory] = psiPackage.getDirectories(scope)
    var packageName: String = psiPackage.getQualifiedName
    for (dir <- dirs; aClass <- JavaDirectoryService.getInstance.getClasses(dir) if aClass.isInstanceOf[ScTypeDefinition]) {
      val qName = aClass.getQualifiedName
      if (qName != null) {
        val index = qName.lastIndexOf('.')
        val s = if (index == -1) "" else qName.substring(0, index)
        if (s == packageName) buffer += aClass
      }
    }
    return buffer.toArray
  }
}