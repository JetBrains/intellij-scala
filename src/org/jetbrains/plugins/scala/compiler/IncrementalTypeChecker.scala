package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import java.io._
import com.intellij.compiler.server.BuildManager
import extensions.using
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.module.ModuleManager
import java.nio.file.Files

/**
 * Nikolay.Tropin
 * 11/20/13
 */
class IncrementalTypeChecker(project: Project) extends ProjectComponent {

  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {

    private val isScalaProject: Boolean = true //todo real method

    def execute(context: CompileContext): Boolean = {

      val previousIncrementalType = getPreviousIncrementalType
      val incrementalType: Option[String] = Option(ScalaApplicationSettings.getInstance().INCREMENTAL_TYPE) //todo real settings

      if (!isScalaProject || incrementalType.isEmpty || previousIncrementalType == incrementalType) return true

      if (context.isRebuild) {
        setPreviousIncrementalType(incrementalType)
        return true
      }

      if (previousIncrementalType.isEmpty) {
        context.addMessage(CompilerMessageCategory.WARNING,
          "scala: cannot find type of the previous incremental compiler, full rebuild may be required", null, -1, -1)
        return true
      }

      if (context.isMake) {
        deleteProjectSystemDirectory()
        setPreviousIncrementalType(incrementalType)
        context.addMessage(CompilerMessageCategory.WARNING,
          "scala: type of incremental compiler has been changed, full rebuild...", null, -1, -1)
      } else {
        context.addMessage(CompilerMessageCategory.ERROR,
          "scala: type of incremental compiler has been changed, full rebuild is required", null, -1, -1)
        return false //cancel compilation
      }

      true
    }
  })

  private def deleteProjectSystemDirectory() {
    def delete(f: File) {
      if (f.isDirectory) {
        f.listFiles.foreach(delete)
      }
      f.delete()
    }

    try {
      val directory = BuildManager.getInstance().getProjectSystemDirectory(project)
      delete(directory)
    }
    catch {
      case e: Exception => throw new IOException("Can not delete project system directory: \n" + e.getMessage)
    }
  }

  private def getPreviousIncrementalType: Option[String] = {
    storageFile.filter(_.exists).flatMap { file =>
      using(new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) { in =>
        try {
          Some(in.readUTF())
        } catch {
          case _: IOException => None
        }
      }
    }
  }

  private def setPreviousIncrementalType(incrTypeOpt: Option[String]) {
    storageFile.foreach { file =>
      incrTypeOpt match {
        case None if file.exists() =>
          try file.delete()
          catch {case e: IOException =>}
        case None =>
        case Some(incrType) =>
          val parentDir = file.getParentFile
          if (!parentDir.exists()) Files.createDirectories(parentDir.toPath)
          using(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            _.writeUTF(incrType)
          }
      }
    }
  }

  private def storageFile: Option[File] = {
    val projectDir = BuildManager.getInstance().getProjectSystemDirectory(project)
    if (projectDir != null)
      Some(new File(projectDir, "incrementalType.dat"))
    else None
  }

  def disposeComponent() {}

  def initComponent() {}

  def projectClosed() {}

  def projectOpened() {}

  def getComponentName: String = getClass.getSimpleName
}
