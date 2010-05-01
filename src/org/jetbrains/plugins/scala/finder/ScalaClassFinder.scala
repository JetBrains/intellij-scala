package org.jetbrains.plugins.scala
package finder

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
import impl.file.PsiPackageImpl
import impl.{JavaPsiFacadeImpl, PsiManagerEx}
import java.util.ArrayList
import com.intellij.openapi.util.Comparing
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import lang.psi.impl.{ScPackageImpl, ScalaPsiManager}

class ScalaClassFinder(project: Project) extends PsiElementFinder {

  def findClass(qName: String, scope: GlobalSearchScope) =
    ScalaCachesManager.getInstance(project).getNamesCache.getClassByFQName(qName, scope)

  def findClasses(qName: String, scope: GlobalSearchScope) = {
    ScalaCachesManager.getInstance(project).getNamesCache.getClassesByFQName(qName, scope)
  }

  override def findPackage(qName: String): PsiPackage = ScalaPsiManager.instance(project).syntheticPackage(qName)
}