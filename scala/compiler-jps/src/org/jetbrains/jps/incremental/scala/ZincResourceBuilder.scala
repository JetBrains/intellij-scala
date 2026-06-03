package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import org.jetbrains.jps.builders.java.{ResourceRootDescriptor, ResourcesTargetType}
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.{BuildOutputConsumer, DirtyFilesHolder}
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, ProgressMessage}
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler
import org.jetbrains.jps.incremental.scala.ZincResourceBuilder.{isEnabled, shouldSkip}
import org.jetbrains.jps.incremental.scala.model.JpsScalaProjectMetadataExtensionService.{customBuildId, moduleHasScala, modulesWithScala}
import org.jetbrains.jps.incremental.scala.sources.{SbtModuleType, SharedSourcesModuleType}
import org.jetbrains.jps.incremental.{CompileContext, ProjectBuildException, ResourcesTarget, TargetBuilder}
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * A modification of [[org.jetbrains.jps.incremental.resources.ResourcesBuilder]].
 * In addition to the original logic, this builder also checks if some copied resources have been
 * modified externally, and copies them again, if needed. This builder handles modules containing
 * Scala, but only if the Zinc incremental compiler is enabled as well.
 */
//noinspection ApiStatus,UnstableApiUsage
private final class ZincResourceBuilder
  extends TargetBuilder[ResourceRootDescriptor, ResourcesTarget](ResourcesTargetType.ALL_TYPES) {

  override def build(
    target: ResourcesTarget,
    holder: DirtyFilesHolder[ResourceRootDescriptor, ResourcesTarget],
    outputConsumer: BuildOutputConsumer,
    context: CompileContext
  ): Unit = {
    val targetModule = target.getModule
    if (!isEnabled(context, targetModule) || shouldSkip(targetModule)) return

    try {
      val skippedRoots = mutable.Map.empty[ResourceRootDescriptor, java.lang.Boolean]
      holder.processDirtyFiles((t, f, srcRoot) => {
        if (isSkipped(t, srcRoot, skippedRoots)) true
        else {
          copyResource(context, srcRoot, f.toPath, outputConsumer)((_, _) => true)
          !context.getCancelStatus.isCanceled
        }
      })

      context.checkCanceled()

      val pd = context.getProjectDescriptor
      val rootIndex = pd.getBuildRootIndex
      val excludeIndex = pd.getModuleExcludeIndex
      val targetRoots = rootIndex.getTargetRoots(target, context)

      val resourceFiles = targetRoots.asScala.toSeq.flatMap { root =>
        val filter = root.createFileFilter()
        val files = Seq.newBuilder[(ResourcesTarget, Path, ResourceRootDescriptor)]
        FileUtil.processFilesRecursively(root.getRootFile, file => {
          if (file.isFile && filter.accept(file) && !excludeIndex.isExcluded(file)) {
            files += ((target, file.toPath, root))
          }
          true
        })
        files.result()
      }

      resourceFiles.foreach { case (t, f, srcRoot) =>
        if (!isSkipped(t, srcRoot, skippedRoots)) {
          copyResource(context, srcRoot, f, outputConsumer)(shouldCopy)
          context.checkCanceled()
        }
      }
    } catch {
      case e: BuildDataCorruptedException => throw e
      case e: ProjectBuildException => throw e
      case e: Exception =>
        //noinspection ReferencePassedToNls
        throw new ProjectBuildException(e.getMessage, e)
    }
  }

  override def getPresentableName: String = builderName

  private def builderName: String = JpsBundle.message("builder.name.zinc.resource.compiler")

  private def isSkipped(
    target: ResourcesTarget,
    root: ResourceRootDescriptor,
    skippedRoots: mutable.Map[ResourceRootDescriptor, java.lang.Boolean]
  ): Boolean = {
    var isSkipped = skippedRoots.get(root).orNull
    if (isSkipped eq null) {
      val outputDir = target.getOutputDir
      isSkipped = java.lang.Boolean.valueOf((outputDir eq null) || FileUtil.filesEqual(outputDir, root.getRootFile))
      skippedRoots += (root -> isSkipped)
    }
    isSkipped.booleanValue()
  }

  private def copyResource(
    context: CompileContext,
    rd: ResourceRootDescriptor,
    file: Path,
    outputConsumer: BuildOutputConsumer
  )(predicate: (Path, Path) => Boolean): Unit = {
    val outputRoot = rd.getTarget.getOutputDir
    if (outputRoot eq null) return

    val sourceRootPath = FileUtilRt.toCanonicalPath(rd.getRootFile.getAbsolutePath, java.io.File.separatorChar, true)
    var relativePath = FileUtilRt.getRelativePath(sourceRootPath, FileUtilRt.toCanonicalPath(file.toAbsolutePath.normalize().toString, java.io.File.separatorChar, true), '/')
    if ("." == relativePath) {
      relativePath = file.getFileName.toString
    }
    val prefix = rd.getPackagePrefix

    val targetPath = new StringBuilder()
    targetPath.append(FileUtil.toCanonicalPath(outputRoot.getPath))
    if (prefix.nonEmpty) {
      targetPath.append('/').append(prefix.replace('.', '/'))
    }
    targetPath.append('/').append(relativePath)

    context.processMessage(
      new ProgressMessage(JpsBundle.message("progress.message.copying.resources.0", rd.getTarget.getModule.getName)))
    try {
      val targetFile = Paths.get(targetPath.toString())
      if (predicate(file, targetFile)) {
        val targetDirectory = targetFile.getParent
        if (!Files.exists(targetDirectory)) {
          Files.createDirectories(targetDirectory)
        }
        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
        outputConsumer.registerOutputFile(targetFile.toFile, java.util.Collections.singletonList(file.toAbsolutePath.normalize().toString))
      }
    } catch {
      case e: Exception =>
        context.processMessage(
          new CompilerMessage(builderName, BuildMessage.Kind.ERROR, CompilerMessage.getTextFromThrowable(e)))
    }
  }

  private def shouldCopy(source: Path, destination: Path): Boolean =
    !Files.exists(destination) || Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(destination)) > 0
}

private object ZincResourceBuilder {

  def isEnabled(context: CompileContext, module: JpsModule): Boolean = {
    val hasScala = moduleHasScala(context)(module)
    val incrementalityType = ScalaBuilder.projectSettings(context).getIncrementalityType
    hasScala && incrementalityType == IncrementalityType.SBT
  }

  def shouldSkip(module: JpsModule): Boolean = {
    val moduleType = module.getModuleType
    moduleType == SbtModuleType.INSTANCE || moduleType == SharedSourcesModuleType.INSTANCE
  }
  
  def createBuilderEnabler(context: CompileContext): StandardResourceBuilderEnabler with ScalaResourceBuilderEnabler = {
    val config = modulesWithScala(context)
    val buildId = customBuildId(context)
    new ZincResourceBuilderEnabler(config, buildId)
  }

  private final class ZincResourceBuilderEnabler(config: Set[String], buildId: Option[UUID])
    extends StandardResourceBuilderEnabler
      with ScalaResourceBuilderEnabler {
    
    override def isResourceProcessingEnabled(module: JpsModule): Boolean = {
      val hasScala = config.contains(module.getName)
      val incrementalityType = ScalaBuilder.projectSettings(module.getProject).getIncrementalityType
      !hasScala || incrementalityType != IncrementalityType.SBT
    }

    override val customBuildId: Option[UUID] = buildId
  }
}
