package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresReadLock}
import org.jetbrains.plugins.scala.project.template.FileExt

import java.io.File
import scala.jdk.CollectionConverters._

private object VfsUtil {

  @RequiresBackgroundThread
  def refreshOutputPaths(project: Project, sources: Set[File]): Unit = {
    val modules = ReadAction.nonBlocking[Array[Module]](() => findModulesForSources(project, sources))
      .inSmartMode(project)
      .expireWhen(() => project.isDisposed)
      .executeSynchronously()
    val outputPaths = CompilerPaths.getOutputPaths(modules).toSeq
    CompilerUtil.refreshOutputRoots(outputPaths.asJavaCollection)
  }

  @RequiresReadLock
  private def findModulesForSources(project: Project, sources: Set[File]): Array[Module] = {
    val fileIndex = ProjectFileIndex.getInstance(project)
    sources.toArray
      .flatMap(_.toVirtualFile)
      .map(fileIndex.getModuleForFile)
      .filter(_ != null) // getModuleForFile is nullable and getOutputPaths doesn't accept nulls
  }
}
