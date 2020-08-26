package org.jetbrains.jps
package incremental
package scala

import _root_.java.io._
import _root_.java.{util => ju}

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.model.module.JpsModule

import _root_.scala.jdk.CollectionConverters._


/**
  * For tasks that should be performed once per compilation
  */
class InitialScalaBuilder extends ModuleLevelBuilder(BuilderCategory.SOURCE_INSTRUMENTER) { //should be before other scala builders

  import InitialScalaBuilder._

  override def getPresentableName = "Collect modules with scala"

  override def buildStarted(context: CompileContext): Unit = collectAndStoreScalaModules(context) match {
    case modules if modules.isEmpty =>
    case _ =>
      checkIncrementalTypeChange(context)
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode =
    ModuleLevelBuilder.ExitCode.NOTHING_DONE

  override def getCompilableFileExtensions: ju.List[String] = ju.Arrays.asList("scala", "java")
}

object InitialScalaBuilder {

  import org.jetbrains.plugins.scala.compiler.IncrementalityType

  private val scalaModulesKey: Key[Set[JpsModule]] =
    Key.create[Set[JpsModule]]("jps.scala.modules")

  def hasScala(context: CompileContext, module: JpsModule): Boolean =
    Option(context.getUserData(scalaModulesKey)).exists(_.contains(module))

  def hasScalaModules(context: CompileContext, chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.exists(hasScala(context, _))

  def isScalaProject(context: CompileContext): Boolean =
    Option(context.getUserData(scalaModulesKey)).exists(_.nonEmpty)


  private def storeScalaModules(context: CompileContext, scalaModules: Set[JpsModule]): Unit = {
    context.putUserData(scalaModulesKey, scalaModules)
  }

  private def collectAndStoreScalaModules(context: CompileContext): Set[JpsModule] = {
    val result = context.getProjectDescriptor.getProject.getModules.asScala
      .filter(SettingsManager.getScalaSdk(_).isDefined)
      .toSet

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

    def setPreviousIncrementalType(incrType: IncrementalityType): Unit = {
      storageFile.foreach { file =>
        val parentDir = file.getParentFile
        if (!parentDir.exists()) parentDir.mkdirs()
        using(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
          _.writeUTF(incrType.name)
        }
      }
    }

    def cleanCaches(): Unit = {
      try {
        val directory = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
        FileUtil.delete(directory)
      }
      catch {
        case e: Exception => throw new IOException("Can not delete project system directory: \n" + e.getMessage)
      }
    }

    val settings = ScalaBuilder.projectSettings(context)
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
