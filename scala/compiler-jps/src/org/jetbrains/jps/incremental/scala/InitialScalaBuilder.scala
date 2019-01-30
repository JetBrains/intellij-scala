package org.jetbrains.jps.incremental.scala

import java.io._
import java.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.scala.ScalaBuilder.projectSettings
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.scala.InitialScalaBuilder._
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

import _root_.scala.collection.JavaConverters._


/**
  * For tasks that should be performed once per compilation
  */
class InitialScalaBuilder extends ModuleLevelBuilder(buildCategory) {

  override def getPresentableName = "Collect modules with scala"

  override def buildStarted(context: CompileContext): Unit = {
    val scalaModules = collectAndStoreScalaModules(context)

    if (scalaModules.nonEmpty) {
      checkIncrementalTypeChange(context)
    }
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode =
    ModuleLevelBuilder.ExitCode.NOTHING_DONE

  override def getCompilableFileExtensions: util.List[String] = util.Arrays.asList("scala", "java")
}

object InitialScalaBuilder {
  //should be before other scala builders
  private def buildCategory = BuilderCategory.SOURCE_INSTRUMENTER

  private val scalaSdkForModulesKey: Key[Map[JpsModule, JpsLibrary]] =
    Key.create[Map[JpsModule, JpsLibrary]]("jps.scala.modules")

  def hasScala(context: CompileContext, module: JpsModule): Boolean =
    Option(context.getUserData(scalaSdkForModulesKey)).exists(_.contains(module))

  def hasScalaModules(context: CompileContext, chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.exists(hasScala(context, _))

  def isScalaProject(context: CompileContext): Boolean =
    Option(context.getUserData(scalaSdkForModulesKey)).exists(_.nonEmpty)

  def scalaSdk(context: CompileContext, module: JpsModule): Option[JpsLibrary] =
    Option(context.getUserData(scalaSdkForModulesKey)).flatMap(_.get(module))

  private def storeScalaModules(context: CompileContext, scalaSdkForModules: Map[JpsModule, JpsLibrary]): Unit = {
    context.putUserData(scalaSdkForModulesKey, scalaSdkForModules)
  }

  private def collectAndStoreScalaModules(context: CompileContext): Map[JpsModule, JpsLibrary] = {
    val project = context.getProjectDescriptor.getProject
    val result = project.getModules.asScala.flatMap { module =>
      Option(SettingsManager.getScalaSdk(module)).map(sdk => module -> sdk)
    }.toMap
    storeScalaModules(context, result)
    result
  }

  //should be called once per compilation and only for scala projects
  private def checkIncrementalTypeChange(context: CompileContext): Unit = {
    def storageFile: Option[File] = {
      val projectDir = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
      if (projectDir != null)
        Some(new File(projectDir, "incrementalType.dat"))
      else None
    }

    def getPreviousIncrementalType: Option[IncrementalityType] = {
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

    def setPreviousIncrementalType(incrType: IncrementalityType) {
      storageFile.foreach { file =>
        val parentDir = file.getParentFile
        if (!parentDir.exists()) parentDir.mkdirs()
        using(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
          _.writeUTF(incrType.name)
        }
      }
    }

    def cleanCaches() {
      context.getProjectDescriptor.setFSCache(FSCache.NO_CACHE)
      try {
        val directory = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
        FileUtil.delete(directory)
      }
      catch {
        case e: Exception => throw new IOException("Can not delete project system directory: \n" + e.getMessage)
      }
    }

    val settings = projectSettings(context)
    val previousIncrementalType = getPreviousIncrementalType
    val incrType = settings.getIncrementalityType
    previousIncrementalType match {
      case _ if JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) => //isRebiuld
        setPreviousIncrementalType(incrType)
      case None =>
      //        ScalaBuilderDelegate.Log.info("scala: cannot find type of the previous incremental compiler, full rebuild may be required")
      case Some(`incrType`) => //same incremental type, nothing to be done
      case Some(_) if isMakeProject(context) =>
        cleanCaches()
        setPreviousIncrementalType(incrType)
        context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING,
          "type of incremental compiler has been changed, full rebuild..."))
      case Some(_) =>
        throw new ProjectBuildException("scala: type of incremental compiler has been changed, full rebuild is required")
    }
  }

  private def isMakeProject(context: CompileContext): Boolean = JavaBuilderUtil.isCompileJavaIncrementally(context) && {
    for {
      chunk <- context.getProjectDescriptor.getBuildTargetIndex.getSortedTargetChunks(context).asScala
      target <- chunk.getTargets.asScala
    } {
      if (!context.getScope.isAffected(target)) return false
    }
    true
  }

}
