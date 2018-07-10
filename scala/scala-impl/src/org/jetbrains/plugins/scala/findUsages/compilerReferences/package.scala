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
  def removeIndexFiles(project: Project): Unit = indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)

  final case class UsagesInFile(file: VirtualFile, lines: Seq[Int]) {
    override def equals(that: scala.Any): Boolean = that match {
      case other: UsagesInFile =>
        file.getPath == other.file.getPath &&
          lines.sorted == other.lines.sorted
      case _ => false
    }
  }

  def withLock[T](lock: Lock)(body: => T): T = {
    lock.lock()

    val result =
      try body
      finally lock.unlock()

    result
  }

  def upToDateCompilerIndexExists(project: Project, expectedVersion: Int): Boolean =
    indexDir(project).exists(
      dir =>
        CompilerReferenceIndex.exists(dir) &&
          !CompilerReferenceIndex.versionDiffers(dir, expectedVersion)
    )
}
