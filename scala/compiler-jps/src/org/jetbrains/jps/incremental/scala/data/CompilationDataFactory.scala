package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaModuleBuildTargetType}
import org.jetbrains.jps.incremental.scala.{ChunkExclusionService, JpsBundle, ModuleBuildTargetUtil, SettingsManager}
import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.{ModuleChunk, ProjectPaths}
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, ZincData}

import java.io.IOException
import java.nio.file.{Files, Path}
import java.util.Collections
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._

trait CompilationDataFactory {

  def from(sources: Seq[Path],
           allSources: Seq[Path],
           context: CompileContext,
           chunk: ModuleChunk): Either[String, CompilationData]
}

object CompilationDataFactory
  extends CompilationDataFactory {

  private val compilationStamp = System.nanoTime()

  // This is an escape hatch from the current Either[String, CompilationData] API, for a recoverable error.
  // The factory needs to be refactored in the future.
  private[scala] final val NoCompilationData = "No compilation data. Skip target."

  override def from(sources: Seq[Path],
                    allSources: Seq[Path],
                    context: CompileContext,
                    chunk: ModuleChunk): Either[String, CompilationData] = {
    val target = chunk.representativeTarget
    val module = target.getModule

    outputsNotSpecified(chunk) match {
      case Some(message) => return Left(message)
      case None =>
    }
    // outputDir is not null here, it has already been checked in `outputsNotSpecified`
    val output = ModuleBuildTargetUtil.outputDir(target).toAbsolutePath.normalize()
    checkOrCreate(output)

    val classpath = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests, false, true).asScala
    val compilerSettings = SettingsManager.getProjectSettings(module.getProject).getCompilerSettings(chunk)
    val sourcePathOptions = sourcePathOptionsFor(context, chunk)
    val scalaOptions = CompilerDataFactory.scalaOptionsFor(compilerSettings, chunk) ++ sourcePathOptions
    val order = compilerSettings.getCompileOrder

    createOutputToCacheMap(context).map { outputToCacheMap =>

      val cacheFile = outputToCacheMap.getOrElse(output, return Left(NoCompilationData))

      val classpathSet = classpath.toSet
      val relevantOutputToCacheMap = (outputToCacheMap - output).filter(p => classpathSet.contains(p._1.toFile))

      val preferredEncoding: Option[String] =
        Option(context.getProjectDescriptor.getEncodingConfiguration.getPreferredModuleChunkEncoding(chunk))

      def ensureEncodingIsExplicitlySet(compilerOptions: Seq[String]): Seq[String] = {
        compilerOptions.find {
          case "-encoding" | "--encoding" => true
          case s"-encoding:${_}" | s"--encoding:${_}" => true
          case _ => false
        } match {
          case Some(_) => compilerOptions
          case None =>
            val encodingOption = preferredEncoding.toSeq.flatMap(Seq("-encoding", _))
            encodingOption ++ compilerOptions
        }
      }

      def filterOutPipeliningOptions(compilerOptions: Seq[String]): Seq[String] = {
        val withoutBooleans = compilerOptions.to(mutable.ArrayBuffer).filter {
          case "-Xjava-tasty" | "-Xpickle-java" | "-Yjava-tasty" | "-Ypickle-java" => false
          case "-Xallow-outline-from-tasty" | "-Yallow-outline-from-tasty" => false
          case "-Ypickle-write-api-only" => false
          case _ => true
        }

        def remove(option: String): Unit = {
          var index = -1
          while ({
            index = withoutBooleans.indexOf(option)
            index != -1
          }) {
            withoutBooleans.remove(index, 2)
          }
        }

        List("-Xearly-tasty-output", "-Xpickle-write", "-Yearly-tasty-output", "-Ypickle-write").foreach(remove)
        withoutBooleans.toSeq
      }

      val javaOptions = CompilerDataFactory.javaOptionsFor(context, chunk) ++ sourcePathOptions

      val outputGroups = createOutputGroups(chunk)

      val canonicalSources = sources.map(_.toAbsolutePath.normalize())

      val isCompile =
        !JavaBuilderUtil.isCompileJavaIncrementally(context) &&
          !JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)

      CompilationData(
        sources = canonicalSources,
        classpath = classpath.map(_.toPath).toSeq,
        output = output,
        scalaOptions = filterOutPipeliningOptions(ensureEncodingIsExplicitlySet(scalaOptions)),
        javaOptions = ensureEncodingIsExplicitlySet(javaOptions),
        order = order,
        cacheFile = cacheFile,
        outputToCacheMap = relevantOutputToCacheMap,
        outputGroups = outputGroups,
        zincData = ZincData(allSources, compilationStamp, isCompile)
      )
    }
  }

  private def sourcePathOptionsFor(context: CompileContext, chunk: ModuleChunk): Seq[String] = {
    val index = context.getProjectDescriptor.getBuildRootIndex
    val target = chunk.representativeTarget()
    val paths = index.getTempTargetRoots(target, context).asScala.map(_.getRootFile).mkString(java.io.File.pathSeparator)
    if (paths.isEmpty) Seq.empty else Seq("-sourcepath", paths)
  }

  private def checkOrCreate(output: Path): Unit = {
    if (!Files.exists(output)) {
      try Files.createDirectories(output)
      catch {
        case t: Throwable => throw new IOException("Cannot create output directory: " + output.toString, t)
      }
    }
  }

  private def outputsNotSpecified(chunk: ModuleChunk): Option[String] = {
    val moduleNames = chunk.getTargets.asScala.filter(ModuleBuildTargetUtil.outputDir(_) == null).map(_.getModule.getName)
    moduleNames.toSeq match {
      case Seq() => None
      case Seq(name) => Some(JpsBundle.message("output.directory.not.specified.for.module.name", name))
      case names => Some(names.mkString(JpsBundle.message("output.directory.not.specified.for.modules"), ", ", ""))
    }
  }

  private def createOutputToCacheMap(context: CompileContext): Either[String, Map[Path, Path]] = {
    val targetToOutput = targetsIn(context).flatMap { target =>
      Option(ModuleBuildTargetUtil.outputDir(target)).map((target, _))
    }

    outputClashesIn(targetToOutput).toLeft {
      val paths = context.getProjectDescriptor.dataManager.getDataPaths

      for ((target, output) <- targetToOutput.toMap)
        yield (
          output.toAbsolutePath.normalize(),
          paths.getTargetDataRootDir(target).resolve(s"cache-${target.getPresentableName}.zip")
        )
    }
  }

  private def createOutputGroups(chunk: ModuleChunk): Seq[(Path, Path)] =
    for {
      target <- chunk.getTargets.asScala.toSeq
      outputDir <- Option(ModuleBuildTargetUtil.outputDir(target)).toSeq
      module = target.getModule
      output = outputDir.toAbsolutePath.normalize()
      sourceRoot <- module.getSourceRoots.asScala if sourceRoot.getRootType.isForTests == target.isTests
      sourceRootFile = sourceRoot.getPath.toAbsolutePath.normalize() if Files.exists(sourceRootFile)
    } yield (sourceRootFile, output)

  private def targetsIn(context: CompileContext): Seq[ModuleBuildTarget] = {
    def isExcluded(target: ModuleBuildTarget): Boolean =
      ChunkExclusionService.isExcluded(chunk(target))

    def isProductionTargetOfTestModule(target: ModuleBuildTarget): Boolean = {
      target.getTargetType == JavaModuleBuildTargetType.PRODUCTION &&
        JpsJavaExtensionService.getInstance.getTestModuleProperties(target.getModule) != null
    }

    val buildTargetIndex = context.getProjectDescriptor.getBuildTargetIndex
    val targets = JavaModuleBuildTargetType.ALL_TYPES.iterator.asScala.flatMap(buildTargetIndex.getAllTargets(_).asScala)

    targets.distinct
      .filterNot(target => buildTargetIndex.isDummy(target) || isExcluded(target) || isProductionTargetOfTestModule(target))
      .to(ArraySeq)
  }

  private def chunk(target: ModuleBuildTarget): ModuleChunk =
    new ModuleChunk(Collections.singleton(target))

  private def outputClashesIn(targetToOutput: Seq[(ModuleBuildTarget, Path)]): Option[String] = {
    val outputToTargetsMap = targetToOutput.groupBy(_._2).view.mapValues(_.map(_._1))

    val errors = outputToTargetsMap.collect {
      case (output, targets) if output != null && targets.length > 1 =>
        val targetNames = targets.map(_.getPresentableName).mkString(", ")
        JpsBundle.message("output.path.shared.between", output, targetNames)
    }

    if (errors.isEmpty) None else Some(
      (errors.toSeq :+ JpsBundle.message("configure.separate.output.paths")).mkString("\n")
    )
  }
}
