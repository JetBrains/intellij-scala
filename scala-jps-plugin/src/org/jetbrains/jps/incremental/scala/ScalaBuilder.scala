package org.jetbrains.jps.incremental.scala

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget, ChunkBuildOutputConsumer, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.SbtData
import collection.JavaConverters._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import sbt.inc.{AnalysisFormats, FileBasedStore, AnalysisStore}

/**
 * @author Pavel Fatin
 */
class ScalaBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  def getPresentableName = "Scala builder"

  def build(context: CompileContext, chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: ChunkBuildOutputConsumer): ModuleLevelBuilder.ExitCode = {

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val filesToCompile = ScalaBuilder.collectCompilableFiles(chunk)

    if (filesToCompile.isEmpty) {
      return ExitCode.NOTHING_DONE
    }

    val sources = filesToCompile.keySet.toSeq

    context.processMessage(new ProgressMessage("Reading compilation settings..."))

    val result =
      ScalaBuilder.compilerFactory.flatMap { compilerFactory =>
        CompilerData.from(context, chunk).flatMap { compilerData =>
          CompilationData.from(sources, context, chunk).map { compilationData =>
            val fileHandler = new ConsumerFileHander(outputConsumer, filesToCompile.asJava)
            val progressHandler = new ProgressHandler(context)

            context.processMessage(new ProgressMessage("Instantiating compiler..."))
            val compiler = compilerFactory.createCompiler(compilerData, ScalaBuilder.createAnalysisStore, context)

            context.processMessage(new ProgressMessage("Compiling..."))
            compiler.compile(compilationData, context, fileHandler, progressHandler)
          }
        }
      }

    result match {
      case Left(error) =>
        context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.ERROR, error))
        ExitCode.ABORT
      case Right(_) =>
        context.processMessage(new ProgressMessage("Compilation completed", 1.0F))
        ExitCode.OK
    }
  }
}

object ScalaBuilder {
  // Globally cached instance of CompilerFactory
  private var cachedCompilerFactory: Option[CompilerFactory] = None

  def compilerFactory: Either[String, CompilerFactory] = cachedCompilerFactory.map(Right(_)).getOrElse {
    val sbtData = {
      val classLoader = getClass.getClassLoader
      val pluginRoot = (new File(PathUtil.getJarPathForClass(getClass))).getParentFile
      val systemRoot = Utils.getSystemRoot
      val javaClassVersion = System.getProperty("java.class.version")

      SbtData.from(classLoader, pluginRoot, systemRoot, javaClassVersion)
    }

    sbtData.map { data =>
      val factory = new CachingFactory(new CompilerFactoryImpl(data), 5, 5)
      cachedCompilerFactory = Some(factory)
      factory
    }
  }

  private def collectCompilableFiles(chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {
    var result = Map[File, BuildTarget[_ <: BuildRootDescriptor]]()

    for (target <- chunk.getTargets.asScala;
         root <- target.getModule.getSourceRoots.asScala) {

      FileUtil.processFilesRecursively(root.getFile, new Processor[File] {
        def process(file: File) = {
          val path = file.getPath
          if (path.endsWith(".scala") || path.endsWith(".java")) {
            result += file -> target
          }
          true
        }
      })
    }

    result
  }

  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)(AnalysisFormats.analysisFormat, AnalysisFormats.setupFormat)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }
}
