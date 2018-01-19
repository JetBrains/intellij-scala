package org.jetbrains.jps.incremental.scala

import _root_.java.util
import java.io.File
import java.util.concurrent.ConcurrentHashMap

import _root_.scala.collection.JavaConverters._
import _root_.scala.collection.mutable

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java._
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.SbtBuilder._
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.scala.local.IdeClientSbt
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import org.jetbrains.jps.incremental.scala.sbtzinc.{CompilerOptionsStore, ModulesFedToZincStore}
import org.jetbrains.jps.incremental.scala.sources.SharedSourcesModuleType
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author Pavel Fatin
 */
class SbtBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  override def getPresentableName = "Scala sbt builder"

  override def buildStarted(context: CompileContext): Unit = {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (isScalaProject(project) && !isDisabled(context))
      JavaBuilder.IS_ENABLED.set(context, false)
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder,
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode = {

    val modules = chunk.getModules.asScala.toSet

    //DirtyFilesHolder is invalidated after build of the chunk is finished,
    //so we have to collect and store dirty files for shared source modules
    val dirtyFilesStorage = dirtyFilesMap(context, chunk)
    if (isSharedSource(chunk)) {
      modules.foreach {
        dirtyFilesStorage.put(_, collectDirtyFiles(dirtyFilesHolder))
      }
    }

    if (isDisabled(context) || ChunkExclusionService.isExcluded(chunk))
      return ExitCode.NOTHING_DONE

    checkIncrementalTypeChange(context)

    updateSharedResources(context, chunk)

    context.processMessage(new ProgressMessage("Searching for compilable files..."))

    val dirtyFilesFromIntellij =
      collectDirtyFiles(dirtyFilesHolder) ++ sourceDependenciesDirtyFiles(chunk, dirtyFilesStorage)

    val moduleNames = modules.map(_.getName).toSeq

    val compilerOptionsChanged = CompilerOptionsStore.updateCompilerOptionsCache(context, chunk, moduleNames)

    if (dirtyFilesFromIntellij.isEmpty &&
      !ModulesFedToZincStore.checkIfAnyModuleDependencyWasFedToZinc(context, chunk) &&
      !compilerOptionsChanged
    ) {
      return ExitCode.NOTHING_DONE
    }


    val sourceToBuildTarget = collectCompilableFiles(context, chunk)
    if (sourceToBuildTarget.isEmpty)
      return ExitCode.NOTHING_DONE

    val allSources = sourceToBuildTarget.keySet.toSeq

    val client = new IdeClientSbt("scala", context, moduleNames, outputConsumer, sourceToBuildTarget.get)

    logCustomSbtIncOptions(context, chunk, client)

    // assume Zinc will be used after we reach this point
    ModulesFedToZincStore.add(context, moduleNames)

    compile(context, chunk, dirtyFilesFromIntellij, allSources, modules, client) match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case Right(code) =>
        if (client.hasReportedErrors || client.isCanceled) {
          ExitCode.ABORT
        } else {
          client.progress("Compilation completed", Some(1.0F))
          code
        }
    }
  }

  override def getCompilableFileExtensions: util.List[String] = util.Arrays.asList("scala", "java")

  private def isDisabled(context: CompileContext): Boolean = {
    projectSettings(context).getIncrementalityType != IncrementalityType.SBT || !isScalaProject(context.getProjectDescriptor.getProject)
  }
}

object SbtBuilder {

  // TODO Mirror file deletion (either via the outputConsumer or a custom index)
  private def updateSharedResources(context: CompileContext, chunk: ModuleChunk) {
    val project = context.getProjectDescriptor

    val resourceTargets: Seq[ResourcesTarget] = {
      val sourceModules = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
      val targetType = chunk.representativeTarget.getTargetType match {
        case JavaModuleBuildTargetType.PRODUCTION => ResourcesTargetType.PRODUCTION
        case JavaModuleBuildTargetType.TEST => ResourcesTargetType.TEST
        case _ => ResourcesTargetType.PRODUCTION
      }
      sourceModules.map(new ResourcesTarget(_, targetType))
    }

    val resourceRoots: Seq[ResourceRootDescriptor] = {
      val rootIndex = project.getBuildRootIndex
      resourceTargets.flatMap(rootIndex.getTargetRoots(_, context).asScala)
    }

    val excludeIndex = project.getModuleExcludeIndex
    val outputRoot: File = chunk.representativeTarget().getOutputDir

    resourceRoots.foreach { root: ResourceRootDescriptor =>
      val filter = root.createFileFilter()

      FileUtil.processFilesRecursively(root.getRootFile, file => {
        if (file.isFile && filter.accept(file) && !excludeIndex.isExcluded(file)) {
          ResourceUpdater.updateResource(context, root, file, outputRoot)
        }
        true
      })
    }
  }

  private def sourceDependenciesDirtyFiles(chunk: ModuleChunk,
                                           dirtyFilesStorage: util.Map[JpsModule, Seq[File]]): Seq[File] = {
    val sourceDependencies = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
    sourceDependencies.flatMap(dirtyFilesStorage.getOrDefault(_, Seq.empty))
  }

  //in current chunk only
  private def collectDirtyFiles(dirtyFilesHolder: DirtyFilesHolder): Seq[File] = {
    val result = collection.mutable.Buffer.empty[File]
    dirtyFilesHolder.processDirtyFiles((_, file, _) => {
      result += file
      true
    })
    result
  }

  private def compilableFiles(context: CompileContext, target: ModuleBuildTarget): Seq[File] = {
    val result = mutable.ArrayBuffer.empty[File]

    val rootIndex = context.getProjectDescriptor.getBuildRootIndex
    val excludeIndex = context.getProjectDescriptor.getModuleExcludeIndex

    for (root <- rootIndex.getTargetRoots(target, context).asScala) {

      FileUtil.processFilesRecursively(root.getRootFile, file => {
        if (!excludeIndex.isExcluded(file)) {
          val fileName = file.getName
          if (fileName.endsWith(".scala") || fileName.endsWith(".java")) {
            result += file
          }
        }
        true
      })
    }
    result
  }

  private def collectCompilableFiles(context: CompileContext,
                                     chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {

    val sourceTargets = sourceDependencyTargets(chunk)

    val fileToTarget =
      for {
        target <- chunk.getTargets.asScala ++ sourceTargets
        file <- compilableFiles(context, target)
      } yield {
        file -> target
      }
    fileToTarget.toMap
  }

  private def sourceDependencyTargets(chunk: ModuleChunk): Seq[ModuleBuildTarget] = {
    val sourceModules = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
    val targetType = chunk.representativeTarget.getTargetType match {
      case javaBuildTarget: JavaModuleBuildTargetType => javaBuildTarget
      case _ => JavaModuleBuildTargetType.PRODUCTION
    }
    sourceModules.map(new ModuleBuildTarget(_, targetType))
  }

  private def logCustomSbtIncOptions(context: CompileContext, chunk: ModuleChunk, client: Client): Unit = {
    val settings = projectSettings(context).getCompilerSettings(chunk)
    val options = settings.getSbtIncrementalOptions
    client.debug(s"Custom sbt incremental compiler options for ${chunk.getPresentableShortName}: ${options.nonDefault}")
  }

  private def isSharedSource(chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.exists {
      _.getModuleType == SharedSourcesModuleType.INSTANCE
    }

  private def dirtyFilesMap(context: CompileContext, chunk: ModuleChunk) = {
    val key = if (chunk.containsTests()) dirtyFilesTestKey else dirtyFilesProductionKey

    Option(context.getUserData(key)).getOrElse {
      val result = new ConcurrentHashMap[JpsModule, Seq[File]]()
      context.putUserData(key, result)
      result
    }
  }

  private val dirtyFilesProductionKey: Key[util.Map[JpsModule, Seq[File]]] =
    Key.create("source.dep.production.dirty.files")

  private val dirtyFilesTestKey: Key[util.Map[JpsModule, Seq[File]]] =
    Key.create("source.dep.test.dirty.files")

  private type DirtyFilesHolder =
    org.jetbrains.jps.builders.DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]
}