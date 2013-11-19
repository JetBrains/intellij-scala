package org.jetbrains.jps.incremental.scala

import _root_.java.util
import java.io.File
import java.net.InetAddress
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{FileProcessor, BuildRootDescriptor, BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.incremental._
import messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.SbtData
import collection.JavaConverters._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{OutputConsumer, ExitCode}
import org.jetbrains.jps.incremental.scala.local.{IdeClientSbt, LocalServer}
import remote.RemoteServer
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.model.module.JpsModule

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
      val dependencies = moduleDependenciesIn(context, representativeTarget)

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
            !hasDirtyFiles(dirtyFilesHolder) &&
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
    val filesToCompile = collectCompilableFiles(context, chunk)

    if (filesToCompile.isEmpty) {
      return ExitCode.NOTHING_DONE
    }

    // Delete dirty class files (to handle force builds and form changes)
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)

    val sources = filesToCompile.keySet.toSeq

    val modules = chunk.getModules.asScala.toSet

    val client = new IdeClientSbt("scala", context, modules.map(_.getName).toSeq, outputConsumer, filesToCompile.get)

    client.progress("Reading compilation settings...")

    compile(context, chunk, sources, modules, client) match {

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


  def compile(context: CompileContext,
              chunk: ModuleChunk,
              sources: Seq[File],
              modules: Set[JpsModule],
              client: IdeClientSbt): Either[String, ModuleLevelBuilder.ExitCode] = {
    for {
      sbtData <-  ScalaBuilder.sbtData
      compilerData <- CompilerData.from(context, chunk)
      compilationData <- CompilationData.from(sources, context, chunk)
    }
    yield {
      scalaLibraryWarning(modules, compilationData, client)

      val server = getServer(context)
      server.compile(sbtData, compilerData, compilationData, client)
    }
  }

  def scalaLibraryWarning(modules: Set[JpsModule], compilationData: CompilationData, client: IdeClientSbt) {
    val hasScalaFacet = modules.exists(SettingsManager.getFacetSettings(_) != null)
    val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

    if (hasScalaFacet && !hasScalaLibrary) {
      val names = modules.map(_.getName).mkString(", ")
      client.warning("No 'scala-library*.jar' in module dependencies [%s]".format(names))
    }
  }

  def getServer(context: CompileContext): Server = {
    val settings = SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)

    if (settings.isCompileServerEnabled && JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null) {
      ScalaBuilder.cleanLocalServerCache()
      new RemoteServer(InetAddress.getByName(null), settings.getCompileServerPort)
    } else {
      ScalaBuilder.localServer
    }
  }

  //we need to override this method by the contract
  override def getCompilableFileExtensions: util.List[String] = List("scala").asJava

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
    val pluginRoot = new File(PathUtil.getJarPathForClass(getClass)).getParentFile
    val systemRoot = Utils.getSystemRoot
    val javaClassVersion = System.getProperty("java.class.version")

    SbtData.from(classLoader, pluginRoot, systemRoot, javaClassVersion)
  }
}