package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.DocumentExt

trait DocumentCompiler {

  def syncAndCompile(project: Project, source: Document): Unit
}

class DocumentCompilerImpl
  extends DocumentCompiler {

  override def syncAndCompile(project: Project, source: Document): Unit =
    for {
      sourceFile <- source.virtualFile
      index = ProjectFileIndex.getInstance(project)
      if isInSources(sourceFile, index) && acceptableExtensions.contains(sourceFile.getExtension)
      module <- getModule(sourceFile, index)
    } {
      source.syncToDisk(project)
      new OneFileRemoteServerConnector(module, sourceFile).compile()
    }

  private def isInSources(sourceFile: VirtualFile,
                          index: ProjectFileIndex): Boolean = {
    val rootType = index.getSourceFolder(sourceFile).getRootType
    JavaModuleSourceRootTypes.SOURCES contains rootType
  }

  private def getModule(sourceFile: VirtualFile,
                        index: ProjectFileIndex): Option[Module] =
    Option(index.getModuleForFile(sourceFile))

  private val acceptableExtensions: Set[String] = Set(
    ScalaFileType.INSTANCE.getDefaultExtension,
  )
}