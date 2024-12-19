package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import org.jetbrains.jps.builders.java.{ResourceRootDescriptor, ResourcesTargetType}
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.{BuildOutputConsumer, DirtyFilesHolder}
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.ZincResourceBuilder.{isEnabled, shouldSkip}
import org.jetbrains.jps.incremental.scala.sources.{SbtModuleType, SharedSourcesModuleType}
import org.jetbrains.jps.incremental.{CompileContext, FSOperations, ProjectBuildException, ResourcesTarget, TargetBuilder}
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import java.io.File
import java.nio.file.Paths
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
          copyResource(context, srcRoot, f, outputConsumer)((_, _) => true)
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
        val files = Seq.newBuilder[(ResourcesTarget, File, ResourceRootDescriptor)]
        FileUtil.processFilesRecursively(root.getRootFile, file => {
          if (file.isFile && filter.accept(file) && !excludeIndex.isExcluded(file)) {
            files += ((target, file, root))
          }
          true
        })
        files.result()
      }

      resourceFiles.foreach { case (t, f, srcRoot) =>
        if (!isSkipped(t, srcRoot, skippedRoots)) {
          copyResource(context, srcRoot, f, outputConsumer) { (file, targetFile) =>
            val resourceTimestamp = file.lastModified()
            val targetTimestamp = targetFile.lastModified()
            resourceTimestamp > targetTimestamp
          }
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
    file: File,
    outputConsumer: BuildOutputConsumer
  )(predicate: (File, File) => Boolean): Unit = {
    val outputRoot = rd.getTarget.getOutputDir
    if (outputRoot eq null) return

    val sourceRootPath = FileUtilRt.toCanonicalPath(rd.getRootFile.getAbsolutePath, File.separatorChar, true)
    var relativePath = FileUtilRt.getRelativePath(sourceRootPath, FileUtilRt.toCanonicalPath(file.getPath, File.separatorChar, true), '/')
    if ("." == relativePath) {
      relativePath = file.getName
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
      val targetFile = Paths.get(targetPath.toString()).toFile
      if (predicate(file, targetFile)) {
        FSOperations.copy(file, targetFile)
        outputConsumer.registerOutputFile(targetFile, java.util.Collections.singletonList(file.getPath))
      }
    } catch {
      case e: Exception =>
        context.processMessage(
          new CompilerMessage(builderName, BuildMessage.Kind.ERROR, CompilerMessage.getTextFromThrowable(e)))
    }
  }
}

private object ZincResourceBuilder {

  def isEnabled(context: CompileContext, module: JpsModule): Boolean = {
    val hasScala = InitialScalaBuilder.hasScala(context, module)
    val incrementalityType = ScalaBuilder.projectSettings(context).getIncrementalityType
    hasScala && incrementalityType == IncrementalityType.SBT
  }

  def shouldSkip(module: JpsModule): Boolean = {
    val moduleType = module.getModuleType
    moduleType == SbtModuleType.INSTANCE || moduleType == SharedSourcesModuleType.INSTANCE
  }
}
