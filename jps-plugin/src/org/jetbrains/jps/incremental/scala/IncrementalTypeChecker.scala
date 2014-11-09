package org.jetbrains.jps.incremental.scala

import java.io._

import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.{CompileContext, FSCache, ProjectBuildException}
import org.jetbrains.jps.incremental.scala.model.IncrementalityType

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 12/5/13
 */
class IncrementalTypeChecker(context: CompileContext) {

  def checkAndUpdate() {
    val settings = SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)
    val previousIncrementalType = getPreviousIncrementalType
    val incrType = settings.getIncrementalityType
    previousIncrementalType match {
      case _ if JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) => //isRebiuld
        setPreviousIncrementalType(incrType)
      case None =>
        ScalaBuilderDelegate.Log.info("scala: cannot find type of the previous incremental compiler, full rebuild may be required")
      case Some(`incrType`) => //same incremental type, nothing to be done
      case Some(_) if isMakeProject =>
        cleanCaches()
        setPreviousIncrementalType(incrType)
        context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING,
          "type of incremental compiler has been changed, full rebuild..."))
      case Some(_) =>
        throw new ProjectBuildException("scala: type of incremental compiler has been changed, full rebuild is required")
    }
  }

  def cleanCaches() {
    context.getProjectDescriptor.setFSCache(FSCache.NO_CACHE)
    deleteProjectSystemDirectory()
  }

  def setPreviousIncrementalType(incrType: IncrementalityType) {
    storageFile.foreach { file =>
      val parentDir = file.getParentFile
      if (!parentDir.exists()) parentDir.mkdirs()
      using(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
        _.writeUTF(incrType.name)
      }
    }
  }

  private def isMakeProject: Boolean = JavaBuilderUtil.isCompileJavaIncrementally(context) && {
    for {
      chunk <- context.getProjectDescriptor.getBuildTargetIndex.getSortedTargetChunks(context).asScala
      target <- chunk.getTargets.asScala
    } {
      if (!context.getScope.isAffected(target)) return false
    }
    true
  }

  private def deleteProjectSystemDirectory() {
    def delete(f: File) {
      if (f.isDirectory) {
        f.listFiles.foreach(delete)
      }
      f.delete()
    }

    try {
      val directory = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
      delete(directory)
    }
    catch {
      case e: Exception => throw new IOException("Can not delete project system directory: \n" + e.getMessage)
    }
  }

  private def getPreviousIncrementalType: Option[IncrementalityType] = {
    storageFile.filter(_.exists).flatMap { file =>
      val result = using(new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) { in =>
        try {
          Some(IncrementalityType.valueOf(in.readUTF()))
        } catch {
          case _: IOException | _: IllegalArgumentException | _: NullPointerException => None
        }
      }
      if (result.isEmpty) file.delete()
      result
    }
  }

  private def storageFile: Option[File] = {
    val projectDir = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
    if (projectDir != null)
      Some(new File(projectDir, "incrementalType.dat"))
    else None
  }
}
