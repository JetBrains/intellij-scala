package org.jetbrains.plugins.scala.lang.psi

import api.toplevel.typedef.{ScObject, ScClass, ScTrait}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{OrderEntry, ProjectRootManager, ProjectFileIndex}
import com.intellij.psi.{PsiClass, JavaPsiFacade, PsiFile}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.module.Module
import java.util.List

object ScalaPsiImplementationHelper {
  def getOriginalClass(psiClass: PsiClass): PsiClass = {
    val psiFile: PsiFile = psiClass.getContainingFile
    val vFile: VirtualFile = psiFile.getVirtualFile
    val project: Project = psiClass.getProject
    val idx: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    if (vFile == null || !idx.isInLibrarySource(vFile)) return psiClass
    val orderEntries: List[OrderEntry] = idx.getOrderEntriesForFile(vFile)
    val fqn: String = psiClass.getQualifiedName
    if (fqn == null) return psiClass
    val classes: Array[PsiClass] = JavaPsiFacade.getInstance(project).findClasses(fqn, new GlobalSearchScope((project)) {
      def compare(file1: VirtualFile, file2: VirtualFile): Int = 0
      def contains(file: VirtualFile): Boolean = {
        val entries: List[OrderEntry] = idx.getOrderEntriesForFile(file)
        var i: Int = 0
        while (i < entries.size) {
          {
            val entry: OrderEntry = entries.get(i)
            if (orderEntries.contains(entry)) return true
          }
            i += 1
        }
        false
      }
      def isSearchInModuleContent(aModule: Module): Boolean = false
      def isSearchInLibraries: Boolean = true
    })
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
}