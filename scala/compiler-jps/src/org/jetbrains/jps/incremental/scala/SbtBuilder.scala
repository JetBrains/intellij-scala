package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java._
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ExitCode => JpsExitCode}
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.scala.SbtBuilder._
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.scala.data.CompilationDataFactory
import org.jetbrains.jps.incremental.scala.local.IdeClientSbt
import org.jetbrains.jps.incremental.scala.model.JpsScalaProjectMetadataExtensionService.{chunkHasScala, projectHasScala}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import _root_.java.nio.file.Path
import _root_.java.util.concurrent.atomic.AtomicBoolean
import _root_.java.{util => jutil}
import _root_.scala.collection.immutable.ArraySeq
import _root_.scala.jdk.CollectionConverters._

class SbtBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  override def getPresentableName: String = JpsBundle.message("sbt.builder.presentable.name")

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder,
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): JpsExitCode = {

    val modules = chunk.getModules.asScala.toSet

    if (!isEnabled(context) || ChunkExclusionService.isExcluded(chunk))
      return JpsExitCode.NOTHING_DONE

    updateSharedResources(context, chunk)

    val dirtyFilesFromIntellij = collectDirtyFiles(dirtyFilesHolder)

    val sourceToBuildTarget = collectCompilableFiles(context, chunk)
    if (sourceToBuildTarget.isEmpty)
      return JpsExitCode.NOTHING_DONE

    val allSources = sourceToBuildTarget.keySet.toSeq

    val hasScalaSources = allSources.exists(_.getFileName.toString.endsWith(".scala"))

    if (hasScalaSources && !chunkHasScala(context)(chunk)) {
      ScalaBuilder.warnChunkHasNoScalaSdk(context, chunk)
      return JpsExitCode.NOTHING_DONE
    }

    val client = new IdeClientSbt("scala", context, chunk, outputConsumer, sourceToBuildTarget.get)

    logCustomSbtIncOptions(context, chunk, client)

    // assume Zinc will be used after we reach this point

    compile(context, chunk, dirtyFilesFromIntellij, allSources, modules, client) match {
      case Left(CompilationDataFactory.NoCompilationData) =>
        JpsExitCode.NOTHING_DONE
      case Left(error) =>
        //noinspection ReferencePassedToNls
        client.error(error)
        JpsExitCode.ABORT
      case Right(code) =>
        if (client.hasReportedErrors || client.isCanceled) {
          JpsExitCode.ABORT
        } else {
          client.progress(JpsBundle.message("compilation.completed"), Some(1.0F))
          exitCode(code)
        }
    }
  }

  override def getCompilableFileExtensions: jutil.List[String] =
    jutil.Arrays.asList("scala", "java")

  private def isEnabled(context: CompileContext): Boolean =
    projectSettings(context).getIncrementalityType == IncrementalityType.SBT && projectHasScala(context)
}

object SbtBuilder {

  // TODO Mirror file deletion (either via the outputConsumer or a custom index)
  // TODO use AdditionalRootsProviderService?
  private def updateSharedResources(context: CompileContext, chunk: ModuleChunk): Unit = {
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
    val outputRoot = ModuleBuildTargetUtil.outputDir(chunk.representativeTarget())
    if (outputRoot eq null) return

    resourceRoots.foreach { (root: ResourceRootDescriptor) =>
      val filter = root.createFileFilter()

      FileUtil.processFilesRecursively(root.getRootFile, file => {
        if (file.isFile && filter.accept(file) && !excludeIndex.isExcluded(file)) {
          ResourceUpdater.updateResource(context, root, file.toPath, outputRoot)
        }
        true
      })
    }
  }

  //in current chunk only
  private def collectDirtyFiles(dirtyFilesHolder: DirtyFilesHolder): Seq[Path] = {
    val builder = Seq.newBuilder[Path]
    dirtyFilesHolder.processDirtyFiles((_, file, _) => {
      builder += file.toPath
      true
    })
    builder.result()
  }

  private def compilableFiles(context: CompileContext, target: ModuleBuildTarget, presentableName: String, loggedMessageOnce: AtomicBoolean): Seq[Path] = {
    val builder = ArraySeq.newBuilder[Path]

    val rootIndex = context.getProjectDescriptor.getBuildRootIndex
    val excludeIndex = context.getProjectDescriptor.getModuleExcludeIndex

    val sourcePathSources = rootIndex.getTempTargetRoots(target, context).asScala.foldLeft(Set.empty[Path]) {
      case (acc, path) =>
        val builder = Set.newBuilder[Path]
        FileUtil.processFilesRecursively(path.getRootFile, file => {
          builder += file.toPath
          true
        })
        acc union builder.result()
    }

    for (root <- rootIndex.getTargetRoots(target, context).asScala) {
      ScalaBuilder.logSearchingForCompilableFiles(context, presentableName, loggedMessageOnce)
      FileUtil.processFilesRecursively(root.getRootFile, file => {
        if (!excludeIndex.isExcluded(file) && !sourcePathSources.contains(file.toPath)) {
          val fileName = file.getName
          if (fileName.endsWith(".scala") || fileName.endsWith(".java")) {
            builder += file.toPath
          }
        }
        true
      })
    }
    builder.result()
  }

  private def collectCompilableFiles(context: CompileContext,
                                     chunk: ModuleChunk): Map[Path, BuildTarget[_ <: BuildRootDescriptor]] = {
    val presentableName = chunk.getPresentableShortName
    val loggedMessageOnce = new AtomicBoolean(false)
    val fileToTarget =
      for {
        target <- chunk.getTargets.asScala
        file <- compilableFiles(context, target, presentableName, loggedMessageOnce)
      } yield {
        file -> target
      }
    fileToTarget.toMap
  }

  private def logCustomSbtIncOptions(context: CompileContext, chunk: ModuleChunk, client: Client): Unit = {
    val settings = projectSettings(context).getCompilerSettings(chunk)
    val options = settings.getSbtIncrementalOptions
    client.internalInfo(s"Custom sbt incremental compiler options for ${chunk.getPresentableShortName}: ${options.nonDefault}")
  }

  private type DirtyFilesHolder =
    org.jetbrains.jps.builders.DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]
}