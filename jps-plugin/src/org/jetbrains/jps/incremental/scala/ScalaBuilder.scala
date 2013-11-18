package org.jetbrains.jps.incremental.scala

import java.io.File
import java.net.InetAddress
import java.util
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{FileProcessor, BuildRootDescriptor, BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.incremental._
import messages.BuildMessage.Kind
import messages.{FileDeletedEvent, CompilerMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.SbtData
import collection.JavaConverters._
import _root_.scala.util.control.Exception._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{OutputConsumer, ExitCode}
import local.LocalServer
import remote.RemoteServer
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}

/**
 * @author Pavel Fatin
 */
class ScalaBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  def getPresentableName = "Scala builder"

  def build(context: CompileContext, chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: OutputConsumer): ModuleLevelBuilder.ExitCode = {

    val representativeTarget = chunk.representativeTarget()

    val timestamps = new TargetTimestamps(context)

    val targetTimestamp = timestamps.get(representativeTarget)

    val hasDirtyDependencies = {
      val dependencies = ScalaBuilder.moduleDependenciesIn(context, representativeTarget)

      targetTimestamp.map { thisTimestamp =>
        dependencies.exists { dependency =>
          val thatTimestamp = timestamps.get(dependency)
          thatTimestamp.map(_ > thisTimestamp).getOrElse(true)
        }
      } getOrElse {
        dependencies.nonEmpty
      }
    }

    if (!hasDirtyDependencies &&
            !ScalaBuilder.hasDirtyFiles(dirtyFilesHolder) &&
            !dirtyFilesHolder.hasRemovedFiles) {

      if (targetTimestamp.isEmpty) {
        timestamps.set(representativeTarget, context.getCompilationStartStamp)
      }

      ExitCode.NOTHING_DONE
    } else {
      timestamps.set(representativeTarget, context.getCompilationStartStamp)

      doBuild(context, chunk, dirtyFilesHolder, outputConsumer)
    }
  }

  private def doBuild(context: CompileContext, chunk: ModuleChunk,
                      dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                      outputConsumer: OutputConsumer): ModuleLevelBuilder.ExitCode = {

    if (ChunkExclusionService.isExcluded(chunk)) {
      return ExitCode.NOTHING_DONE
    }

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val filesToCompile = ScalaBuilder.collectCompilableFiles(context, chunk)

    if (filesToCompile.isEmpty) {
      return ExitCode.NOTHING_DONE
    }

    // Delete dirty class files (to handle force builds and form changes)
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)

    val sources = filesToCompile.keySet.toSeq

    val modules = chunk.getModules.asScala

    val client = new IdeClient("scala", context, modules.map(_.getName).toSeq, outputConsumer, filesToCompile.get)

    client.progress("Reading compilation settings...")

    ScalaBuilder.sbtData.flatMap { sbtData =>
      CompilerData.from(context, chunk).flatMap { compilerData =>
        CompilationData.from(sources, context, chunk).map { compilationData =>
          val hasScalaSdk = modules.exists(SettingsManager.hasScalaSdk)
          val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

          if (hasScalaSdk && !hasScalaLibrary) {
            val names = modules.map(_.getName).mkString(", ")
            client.warning("No 'scala-library*.jar' in module dependencies [%s]".format(names))
          }

          val settings = SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)

          val server = if (settings.isCompileServerEnabled && JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null) {
            ScalaBuilder.cleanLocalServerCache()
            new RemoteServer(InetAddress.getByName(null), settings.getCompileServerPort)
          } else {
            ScalaBuilder.localServer
          }

          server.compile(sbtData, compilerData, compilationData, client)
        }
      }
    } match {
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
}

object ScalaBuilder {
  val Log = JpsLogger.getInstance(classOf[ScalaBuilder])

  // Cached local localServer
  private var cachedServer: Option[Server] = None

  private val lock = new Object()

  private def localServer = {
    lock.synchronized {
      val server = cachedServer.getOrElse(new LocalServer())
      cachedServer = Some(server)
      server
    }
  }

  private def cleanLocalServerCache() {
    lock.synchronized {
      cachedServer = None
    }
  }

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

  private def collectCompilableFiles(context: CompileContext,chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {
    var result = Map[File, BuildTarget[_ <: BuildRootDescriptor]]()

    val project = context.getProjectDescriptor

    val rootIndex = project.getBuildRootIndex
    val excludeIndex = project.getModuleExcludeIndex

    for (target <- chunk.getTargets.asScala;
         root <- rootIndex.getTargetRoots(target, context).asScala) {
      FileUtil.processFilesRecursively(root.getRootFile, new Processor[File] {
        def process(file: File) = {
          if (!excludeIndex.isExcluded(file)) {
            val path = file.getPath
            if (path.endsWith(".scala") || path.endsWith(".java")) {
              result += file -> target
            }
          }
          true
        }
      })
    }

    result
  }

  private def moduleDependenciesIn(context: CompileContext, target: ModuleBuildTarget): Seq[ModuleBuildTarget] = {
    val dependencies = {
      val targetOutputIndex = {
        val targets = context.getProjectDescriptor.getBuildTargetIndex.getAllTargets
        new TargetOutputIndexImpl(targets, context)
      }
      target.computeDependencies(context.getProjectDescriptor.getBuildTargetIndex, targetOutputIndex).asScala
    }

    dependencies.filter(_.isInstanceOf[ModuleBuildTarget]).map(_.asInstanceOf[ModuleBuildTarget]).toSeq
  }
}

private class IdeClient(compilerName: String,
                        context: CompileContext,
                        modules: Seq[String],
                        consumer: OutputConsumer,
                        sourceToTarget: File => Option[BuildTarget[_ <: BuildRootDescriptor]]) extends Client {

  private var hasErrors = false

  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    if (kind == Kind.ERROR) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)

    context.processMessage(new CompilerMessage(name, kind, text, sourcePath.orNull,
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

  def debug(text: String) {
    ScalaBuilder.Log.info(text)
  }

  def generated(source: File, module: File, name: String) {
    invalidateBoundForms(source)
    val target = sourceToTarget(source).getOrElse {
      throw new RuntimeException("Unknown source file: " + source)
    }
    val compiledClass = new LazyCompiledClass(module, source, name)
    consumer.registerCompiledClass(target, compiledClass)
  }

  // TODO Expect JPS compiler in UI-designer to take generated class events into account
  private val FormsToCompileKey = catching(classOf[ClassNotFoundException], classOf[NoSuchFieldException]).opt {
    val field = Class.forName("org.jetbrains.jps.uiDesigner.compiler.FormsBuilder").getDeclaredField("FORMS_TO_COMPILE")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Key[util.Map[File, util.Collection[File]]]]
  }

  private def invalidateBoundForms(source: File) {
    FormsToCompileKey.foreach { key =>
      val boundForms: Option[Iterable[File]] = {
        val sourceToForm = context.getProjectDescriptor.dataManager.getSourceToFormMap
        val sourcePath = FileUtil.toCanonicalPath(source.getPath)
        Option(sourceToForm.getState(sourcePath)).map(_.asScala.map(new File(_)))
      }

      boundForms.foreach { forms =>
        val formsToCompile = Option(key.get(context)).getOrElse(new util.HashMap[File, util.Collection[File]]())
        formsToCompile.put(source, forms.toVector.asJava)
        key.set(context, formsToCompile)
      }
    }
  }

  def deleted(module: File) {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  def isCanceled = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors
}

// TODO expect future JPS API to load the generated file content lazily (on demand)
private class LazyCompiledClass(outputFile: File, sourceFile: File, className: String)
        extends CompiledClass(outputFile, sourceFile, className, new BinaryContent(Array.empty)){

  private var loadedContent: Option[BinaryContent] = None
  private var contentIsSet = false

  override def getContent = {
    if (contentIsSet) super.getContent else loadedContent.getOrElse {
      val content = new BinaryContent(FileUtil.loadFileBytes(outputFile))
      loadedContent = Some(content)
      content
    }
  }

  override def setContent(content: BinaryContent) {
    super.setContent(content)
    loadedContent = None
    contentIsSet = true
  }
}