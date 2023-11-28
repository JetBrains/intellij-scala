package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.scala.InitialScalaBuilder.isScalaProject
import org.jetbrains.jps.incremental.scala.data.{CompilationDataFactory, DataFactoryService}
import org.jetbrains.jps.incremental.scala.local.IdeClientSbt
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import scala.jdk.CollectionConverters._

private final class ScalaClassPostProcessorBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  override def build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer: ModuleLevelBuilder.OutputConsumer
  ): ModuleLevelBuilder.ExitCode = {
    if (!isEnabled(context)) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val outputFiles = (for {
      target <- chunk.getTargets.asScala
      compiledClass <- outputConsumer.getTargetCompiledClasses(target).asScala
    } yield compiledClass.getOutputFile.toPath).toSeq

    if (outputFiles.isEmpty) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val client = new IdeClientSbt("scala", context, chunk, outputConsumer, _ => None)

    DataFactoryService.instance(context)
      .getCompilationDataFactory
      .from(Seq.empty, Seq.empty, context, chunk)
      .map(_.cacheFile.toPath) match {
      case Left(CompilationDataFactory.NoCompilationData) =>
        ModuleLevelBuilder.ExitCode.NOTHING_DONE
      case Left(error) =>
        //noinspection ReferencePassedToNls
        client.error(error)
        ModuleLevelBuilder.ExitCode.ABORT
      case Right(analysisFile) =>
        val code = ScalaBuilder.computeStamps(context, chunk, outputFiles, analysisFile, client)
        ScalaBuilder.exitCode(code)
    }
  }

  override def getCompilableFileExtensions: java.util.List[String] = java.util.Collections.emptyList()

  override def getPresentableName: String = JpsBundle.message("scala.class.post.processor.builder.presentable.name")

  private def isEnabled(context: CompileContext): Boolean =
    isScalaProject(context) && ScalaBuilder.projectSettings(context).getIncrementalityType == IncrementalityType.SBT
}
