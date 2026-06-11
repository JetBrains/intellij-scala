package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.{LibraryEntity, SdkEntity}
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.jdk.CollectionConverters.CollectionHasAsScala

object ScalaPsiImplementationHelper {
  def getOriginalClass(psiClass: PsiClass): PsiClass = {
    val psiFile: PsiFile = psiClass.getContainingFile
    val vFile: VirtualFile = psiFile.getVirtualFile
    val project: Project = psiClass.getProject
    val idx: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    if (vFile == null || !idx.isInLibrarySource(vFile)) return psiClass
    val originalEntities = findAssociatedEntities(vFile, idx).toList
    val fqn: String = psiClass.qualifiedName
    if (fqn == null) return psiClass
    val classes: Array[PsiClass] = ScalaPsiManager.instance(project).getCachedClasses(new GlobalSearchScope(project) {
      override def compare(file1: VirtualFile, file2: VirtualFile): Int = 0
      override def contains(file: VirtualFile): Boolean = {
        val myEntities = findAssociatedEntities(file, idx)
        for (element <- myEntities) {
          if (originalEntities.contains(element)) return true
        }
        false
      }
      override def isSearchInModuleContent(aModule: Module): Boolean = false
      override def isSearchInLibraries: Boolean = true
    }, fqn)
    if (classes.length == 0) psiClass
    else if (classes.length == 1) classes(0)
    else {
      psiClass match {
        case _: ScTrait | _: ScClass =>
          classes.find(td => td.isInstanceOf[ScTrait] || td.isInstanceOf[ScClass]).getOrElse(classes(0))
        case _: ScObject =>
          classes.find(td => td.isInstanceOf[ScObject]).getOrElse(classes(0))
        case _ => classes(0)
      }
    }
  }

  /**
   * Searches for corresponding [[WorkspaceModel]] entities for a given file.
   *
   *
   * First, it searches for [[LibraryEntity]]s associated with the file. If no [[LibraryEntity]]s found,
   * it searches for [[SdkEntity]]s associated with the file.
   */
  def findAssociatedEntities(file: VirtualFile, idx: ProjectFileIndex): Iterable[WorkspaceEntityWithSymbolicId] = {
    val elements = idx.findContainingLibraries(file).asScala

    if (elements.nonEmpty) {
      elements
    } else {
      idx.findContainingSdks(file).asScala
    }
  }
}