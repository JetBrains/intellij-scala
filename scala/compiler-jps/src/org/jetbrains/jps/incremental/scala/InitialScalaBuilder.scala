package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import _root_.java.io._
import _root_.java.nio.charset.StandardCharsets
import _root_.java.nio.file.Files
import _root_.java.{util => ju}
import _root_.scala.jdk.CollectionConverters._

/**
  * For tasks that should be performed once per compilation
  */
class InitialScalaBuilder extends ModuleLevelBuilder(BuilderCategory.SOURCE_INSTRUMENTER) { //should be before other scala builders

  import InitialScalaBuilder._

  override def getPresentableName: String = JpsBundle.message("scala.compiler.metadata.builder")

  override def buildStarted(context: CompileContext): Unit = {
    val scalaModules = collectAndStoreScalaModules(context)
    if (scalaModules.nonEmpty) {
      val previousIncrementalityType = readIncrementalityType(context)
      val incrementalityType = ScalaBuilder.projectSettings(context).getIncrementalityType

      previousIncrementalityType match {
        case _ if JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) =>
          // Forced rebuild, save current incremental compiler setting to disk and continue.
          // This case is entered after the rebuild requested exception is thrown later in the file.
          Log.info("Forced project rebuild initiated, saving incremental compiler setting to disk and continuing")
          writeIncrementalityType(context, incrementalityType)
        case None =>
          Log.warn("Previous incremental compiler setting was not read from disk, continuing with the current incremental compiler project setting, compilation errors are possible")
        case Some(`incrementalityType`) =>
          Log.info("Previous incremental compiler setting matches the current incremental compiler project setting, continuing")
        case Some(_) if isMakeProject(context) =>
          // All build targets are affected, full rebuild, save current incremental compiler setting to disk and continue
          Log.info("Full project rebuild, saving incremental compiler setting to disk and continuing")
          writeIncrementalityType(context, incrementalityType)
        case Some(_) =>
          // Not a full rebuild, and incremental compiler setting has been changed since the last build, forcing a rebuild
          Log.info("Previous incremental compiler setting does not match current project setting and the build is not a full rebuild, forcing a project rebuild")
          val isCBH = Option(context.getBuilderParameter(BuildParameters.BuildTriggeredByCBH)).flatMap(_.toBooleanOption).getOrElse(false)
          if (isCBH) {
            // Compiler based highlighting specific workaround. Because of the way we create the compilation scopes in
            // CBH to be as minimal as possible, JPS does not propagate the rebuild requested exception that we throw
            // in this case. This might be a bug, I plan to speak to the JPS team about this.
            writeIncrementalityType(context, incrementalityType)
          }
          val message = JpsBundle.message("incremental.compiler.changed.rebuild")
          throw new BuildDataCorruptedException(message)
      }
    }
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode =
    ModuleLevelBuilder.ExitCode.NOTHING_DONE

  override def getCompilableFileExtensions: ju.List[String] = ju.Arrays.asList("scala", "java")

  private def incrementalityTypeStorageFile(context: CompileContext): File = {
    val dataStorageRoot = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
    new File(dataStorageRoot, "incrementalType.dat")
  }

  private def readIncrementalityType(context: CompileContext): Option[IncrementalityType] = {
    val storageFile = incrementalityTypeStorageFile(context)
    if (!storageFile.exists()) {
      // This is not necessarily an error, the storage file will be created.
      val projectName = context.getProjectDescriptor.getProject.getName
      Log.info(s"Incremental compiler project setting storage file does not exist for project $projectName, path: ${storageFile.toPath}")
      return None
    }

    val result = try {
      val bytes = Files.readAllBytes(storageFile.toPath)
      val str = new String(bytes, StandardCharsets.UTF_8)
      val incrementality = IncrementalityType.valueOf(str)
      Some(incrementality)
    } catch {
      case e: IOException =>
        Log.error("Could not read incremental compiler settings from disk", e)
        None
      case e: IllegalArgumentException =>
        Log.error("Unknown incrementality type string", e)
        None
    }

    if (result.isEmpty) storageFile.delete()
    result
  }

  private def writeIncrementalityType(context: CompileContext, incrementalityType: IncrementalityType): Unit = {
    val storageFile = incrementalityTypeStorageFile(context)
    val parentDir = storageFile.getParentFile
    if (!parentDir.exists()) parentDir.mkdirs()
    val storagePath = storageFile.toPath
    try {
      Files.write(storagePath, incrementalityType.name().getBytes(StandardCharsets.UTF_8))
      Log.info(s"Wrote incremental compiler setting $incrementalityType to disk, path: $storagePath")
    }
    catch {
      case e: IOException =>
        Log.error(s"Could not write incremental compiler setting $incrementalityType to disk, path: $storagePath", e)
    }
  }

  private def isMakeProject(context: CompileContext): Boolean = {
    def allTargetsAffected: Boolean =
      context.getProjectDescriptor.getBuildTargetIndex.getSortedTargetChunks(context).asScala.forall { chunk =>
        chunk.getTargets.asScala.forall { target =>
          context.getScope.isAffected(target)
        }
      }

    JavaBuilderUtil.isCompileJavaIncrementally(context) && allTargetsAffected
  }
}

object InitialScalaBuilder {
  private val Log: Logger = Logger.getInstance(classOf[InitialScalaBuilder])

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
}
