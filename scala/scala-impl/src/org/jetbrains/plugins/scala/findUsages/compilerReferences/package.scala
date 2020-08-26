package org.jetbrains.plugins.scala.findUsages

import java.io.File
import java.util.concurrent.locks.Lock

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import org.jetbrains.plugins.scala.indices.protocol.sbt.SbtCompilationInfo
import org.jetbrains.sbt.project.data.ModuleNode

package object compilerReferences {
  private def buildDir(project: Project): Option[File] =
    Option(BuildManager.getInstance().getProjectSystemDirectory(project))

  def indexDir(project: Project): Option[File] = buildDir(project).map(new File(_, "scala-compiler-references"))
  def removeIndexFiles(project: Project): Unit = indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)

  final case class UsagesInFile(file: VirtualFile, lines: collection.Seq[Int]) {
    override def equals(that: scala.Any): Boolean = that match {
      case other: UsagesInFile =>
        file.getPath == other.file.getPath &&
          lines.sorted == other.lines.sorted
      case _ => false
    }
  }

  def task(project: Project, @Nls title: String)(body: ProgressIndicator => Any): Task.Backgroundable =
    new Task.Backgroundable(project, title, false) {
      override def run(indicator: ProgressIndicator): Unit = body(indicator)
    }

  implicit class LockExt(private val lock: Lock) extends AnyVal {
    def locked[T](body: => T): T = {
      lock.lock()

      val result =
        try body
        finally lock.unlock()

      result
    }
  }

  implicit class CompilationInfoExt(private val info: CompilationInfo) extends AnyVal {
    def affectedModules(project: Project): Set[Module] = {
      val manager = ModuleManager.getInstance(project)

      info match {
        case jinfo: JpsCompilationInfo   => jinfo.affectedModules.flatMap(manager.findModuleByName(_).toOption)
        case sbtInfo: SbtCompilationInfo => findIdeaModule(project, sbtInfo.projectId).toSet
        case _                           => Set.empty
      }
    }
  }

  def findIdeaModule(project: Project, sbtProjectId: String): Option[Module] = {
    val projectBaseUri = new File(project.getBasePath).toURI
    val moduleId = ModuleNode.combinedId(sbtProjectId, Option(projectBaseUri))

    ModuleManager.getInstance(project).getModules.find(module =>
      ExternalSystemApiUtil.getExternalProjectId(module) == moduleId
    )
  }

  def upToDateCompilerIndexExists(project: Project, expectedVersion: Int): Boolean =
    indexDir(project).exists(
      dir =>
        CompilerReferenceIndex.exists(dir) &&
          !CompilerReferenceIndex.versionDiffers(dir, expectedVersion)
    )
}
