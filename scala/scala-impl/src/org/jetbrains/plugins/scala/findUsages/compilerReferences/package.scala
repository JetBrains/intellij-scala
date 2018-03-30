package org.jetbrains.plugins.scala.findUsages

import java.io.File
import java.util.concurrent.locks.Lock

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex

package object compilerReferences {
  private def buildDir(project: Project): Option[File] =
    Option(BuildManager.getInstance().getProjectSystemDirectory(project))
  
  def indexDir(project: Project): Option[File] = buildDir(project).map(new File(_, "scala-compiler-references"))

  final case class LinesWithUsagesInFile(file: VirtualFile, lines: Set[Int])

  def withLock[T](lock: Lock)(body: => T): T = {
    lock.lock()
    val result = body
    lock.unlock()
    result
  }

  def upToDateCompilerIndexExists(project: Project): Boolean =
    indexDir(project).exists(CompilerReferenceIndex.versionDiffers(_, ScalaCompilerIndices.getIndices))
}
