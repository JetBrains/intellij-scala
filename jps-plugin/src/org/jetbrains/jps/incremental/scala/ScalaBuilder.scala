package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.javac.BinaryContent
import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{FileProcessor, BuildRootDescriptor, BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental._
import messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.SbtData
import collection.JavaConverters._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{OutputConsumer, ExitCode}
import org.jetbrains.jps.model.java.JavaSourceRootType
import local.LocalServer
import remote.RemoteServer

/**
 * @author Pavel Fatin
 */
class ScalaBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  def getPresentableName = "Scala builder"

  def build(context: CompileContext, chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: OutputConsumer): ModuleLevelBuilder.ExitCode = {

    if (!ScalaBuilder.hasDirtyFiles(dirtyFilesHolder) && !dirtyFilesHolder.hasRemovedFiles) {
      return ExitCode.NOTHING_DONE
    }

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val filesToCompile = ScalaBuilder.collectCompilableFiles(chunk)

    if (filesToCompile.isEmpty) {
      return ExitCode.NOTHING_DONE
    }

    // Delete dirty class files (to handle force builds and form changes)
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)

    val sources = filesToCompile.keySet.toSeq

    val client = {
      val modules = chunk.getModules.asScala.map(_.getName).toSeq
      val compilerName = if (sources.exists(_.getName.endsWith(".scala"))) "scala" else "java"
      new IdeClient(compilerName, context, modules, outputConsumer, filesToCompile.get)
    }

    client.progress("Reading compilation settings...")

    ScalaBuilder.sbtData.flatMap { sbtData =>
      CompilerData.from(context, chunk).flatMap { compilerData =>
        CompilationData.from(sources, context, chunk).map { compilationData =>
//          val server = new RemoteServer("localhost", 2113)
          ScalaBuilder.server.compile(sbtData, compilerData, compilationData, client)
        }
      }
    } match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case Right(code) =>
        client.progress("Compilation completed", Some(1.0F))
        code
    }
  }
}

object ScalaBuilder {
  private val server = new LocalServer()

  private lazy val sbtData = {
    val classLoader = getClass.getClassLoader
    val pluginRoot = (new File(PathUtil.getJarPathForClass(getClass))).getParentFile
    val systemRoot = Utils.getSystemRoot
    val javaClassVersion = System.getProperty("java.class.version")

    SbtData.from(classLoader, pluginRoot, systemRoot, javaClassVersion)
  }

  private def hasDirtyFiles(dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Boolean = {
    var result = false

    dirtyFilesHolder.processDirtyFiles(new FileProcessor[JavaSourceRootDescriptor, ModuleBuildTarget] {
      def apply(target: ModuleBuildTarget, file: File, root: JavaSourceRootDescriptor) = {
        result = true
        false
      }
    })

    result
  }

  private def collectCompilableFiles(chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {
    var result = Map[File, BuildTarget[_ <: BuildRootDescriptor]]()

    for (target <- chunk.getTargets.asScala;
         rootType = if (target.isTests) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE;
         root <- target.getModule.getSourceRoots(rootType).asScala) {

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
}

private class IdeClient(compilerName: String,
                        context: CompileContext,
                        modules: Seq[String],
                        consumer: OutputConsumer,
                        sourceToTarget: File => Option[BuildTarget[_ <: BuildRootDescriptor]]) extends Client {

  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    val sourcePath = source.map(file => FileUtil.toCanonicalPath(file.getPath))

    context.processMessage(new CompilerMessage(compilerName, kind, text, sourcePath.orNull,
      -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
  }

  def trace(exception: Throwable) {
    context.processMessage(new CompilerMessage(compilerName, exception))
  }

  def progress(text: String, done: Option[Float]) {
    val formattedText = if (text.isEmpty) "" else {
      val decapitalizedText = text.charAt(0).toLower.toString + text.substring(1)
      "%s: %s [%s]".format(compilerName, decapitalizedText, modules.mkString(", "))
    }
    context.processMessage(new ProgressMessage(formattedText, done.getOrElse(-1.0F)))
  }

  def generated(source: File, module: File, name: String) {
    val target = sourceToTarget(source).getOrElse {
      throw new RuntimeException("Unknown source file: " + source)
    }
    val compiledClass = {
      // TODO expect future JPS API to load the generated file content lazily (on demand)
      val content = new BinaryContent(FileUtil.loadFileBytes(module))
      new CompiledClass(module, source, name, content)
    }
    consumer.registerCompiledClass(target, compiledClass)
  }

  def isCanceled = context.getCancelStatus.isCanceled
}