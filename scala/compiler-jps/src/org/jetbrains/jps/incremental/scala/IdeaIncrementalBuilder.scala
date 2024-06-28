package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.data.CompilationDataFactory
import org.jetbrains.jps.incremental.{java => _, scala => _, _}
import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, IncrementalityType}

import java.io.File
import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class IdeaIncrementalBuilder(category: BuilderCategory) extends ModuleLevelBuilder(category) {

  import ModuleLevelBuilder.{ExitCode => JpsExitCode}

  override def getPresentableName: String = JpsBundle.message("idea.incremental.builder.presentable.name")

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): JpsExitCode = {

    if (!isEnabled(context, chunk) || ChunkExclusionService.isExcluded(chunk))
      return JpsExitCode.NOTHING_DONE

    context.processMessage(new ProgressMessage(JpsBundle.message("searching.for.compilable.files.0", chunk.getPresentableShortName)))

    val sourceDependencies = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
    if (sourceDependencies.nonEmpty) {
      val message = "IDEA incremental compiler cannot handle shared source modules: " +
        sourceDependencies.map(_.getName).mkString(", ") +
        ".\nPlease enable sbt incremental compiler for the project."
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.ERROR, message))
      return JpsExitCode.ABORT
    }

    val sources = collectSources(context, chunk, dirtyFilesHolder)
    if (sources.isEmpty) return JpsExitCode.NOTHING_DONE

    if (ScalaBuilder.hasBuildModules(chunk)) return JpsExitCode.NOTHING_DONE // *.scala files in sbt "build" modules are rightly excluded from compilation

    if (!InitialScalaBuilder.hasScalaModules(context, chunk)) {
      val message = "skipping Scala files without a Scala SDK in module(s) " + chunk.getPresentableShortName
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
      return JpsExitCode.NOTHING_DONE
    }

    val packageObjectsData = local.PackageObjectsData.getFor(context)
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) { //rebuild
      packageObjectsData.clear()
    }
    else {
      val additionalFiles = packageObjectsData.invalidatedPackageObjects(sources).filter(_.exists)
      if (additionalFiles.nonEmpty) {
        (sources ++ additionalFiles).foreach(f => FSOperations.markDirty(context, CompilationRound.NEXT, f))
        return JpsExitCode.ADDITIONAL_PASS_REQUIRED
      }
    }

    val callback = JavaBuilderUtil.getDependenciesRegistrar(context)

    val modules = chunk.getModules.asScala.toSet

    val successfullyCompiled = mutable.Set.empty[File]

    val compilerName = "scalac"

    val client = new local.IdeClientIdea(compilerName, context, chunk, outputConsumer,
      callback, successfullyCompiled, packageObjectsData)

    val scalaSources = sources.filter(_.getName.endsWith(".scala")).asJava

    ScalaBuilder.compile(context, chunk, sources, Seq.empty, modules, client) match {
      case Left(CompilationDataFactory.NoCompilationData) =>
        JpsExitCode.NOTHING_DONE
      case Left(error) =>
        //noinspection ReferencePassedToNls
        client.error(error)
        JpsExitCode.ABORT
      case _ if client.hasReportedErrors || client.isCanceled => JpsExitCode.ABORT
      case Right(code) =>
        JavaBuilderUtil.registerFilesToCompile(context, scalaSources)
        JavaBuilderUtil.registerSuccessfullyCompiled(context, successfullyCompiled.asJava)
        client.progress(JpsBundle.message("compilation.completed"), Some(1.0F))
        ScalaBuilder.exitCode(code)
    }
  }

  override def getCompilableFileExtensions: ju.List[String] =
    ju.Arrays.asList("scala", "java")

  private def isEnabled(context: CompileContext, chunk: ModuleChunk): Boolean = {
    val settings = ScalaBuilder.projectSettings(context)

    def correctIncrementalityType: Boolean = settings.getIncrementalityType == IncrementalityType.IDEA

    def correctCompileOrder: Boolean = {
      val compileOrder = settings.getCompilerSettings(chunk).getCompileOrder
      val category = getCategory
      compileOrder match {
        case CompileOrder.JavaThenScala =>
          // Java code needs to be compiled before Scala. The Java JPS builder will compile the Java code, and it runs
          // with a TRANSLATOR builder category. We need to run after it. OVERWRITING_TRANSLATOR runs after TRANSLATOR.
          category == BuilderCategory.OVERWRITING_TRANSLATOR
        case CompileOrder.ScalaThenJava | CompileOrder.Mixed =>
          category == BuilderCategory.SOURCE_PROCESSOR
      }
    }

    correctIncrementalityType && correctCompileOrder
  }

  private def collectSources(context: CompileContext,
                             chunk: ModuleChunk,
                             dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Seq[File] = {

    val builder = Seq.newBuilder[File]

    val project = context.getProjectDescriptor

    val compileOrder = ScalaBuilder.projectSettings(context).getCompilerSettings(chunk).getCompileOrder
    val extensionsToCollect = compileOrder match {
      case CompileOrder.Mixed => List(".scala", ".java")
      case _ => List(".scala")
    }

    def checkAndCollectFile(file: File): Boolean = {
      val fileName = file.getName
      if (extensionsToCollect.exists(fileName.endsWith))
        builder += file

      true
    }

    dirtyFilesHolder.processDirtyFiles((_: ModuleBuildTarget, file: File, _: JavaSourceRootDescriptor) => checkAndCollectFile(file))

    for {
      target <- chunk.getTargets.asScala
      tempRoot <- project.getBuildRootIndex.getTempTargetRoots(target, context).asScala
    } {
      FileUtil.processFilesRecursively(tempRoot.getRootFile, (file: File) => checkAndCollectFile(file))
    }


    //if no scala files to compile, return empty seq
    val result = builder.result()
    if (!result.exists(_.getName.endsWith(".scala"))) Seq.empty
    else result
  }
}
