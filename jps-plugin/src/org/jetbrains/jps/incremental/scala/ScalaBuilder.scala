package org.jetbrains.jps.incremental.scala

import java.io.File
import java.net.InetAddress
import java.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.remote.RemoteServer
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

import _root_.scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 11/19/13
 */
abstract class ScalaBuilder(category: BuilderCategory) extends ModuleLevelBuilder(category) {
  def getPresentableName: String = "Scala builder"

  def build(context: CompileContext,
            chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode = {

    new IncrementalTypeChecker(context).checkAndUpdate()

    if (isDisabled(context) || !isNeeded(context, chunk, dirtyFilesHolder))
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE

    doBuild(context, chunk, dirtyFilesHolder, outputConsumer)
  }

  def doBuild(context: CompileContext,
              chunk: ModuleChunk,
              dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
              outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode

  def compile(context: CompileContext,
              chunk: ModuleChunk,
              sources: Seq[File],
              modules: Set[JpsModule],
              client: Client): Either[String, ModuleLevelBuilder.ExitCode] = {
    for {
      sbtData <-  sbtData
      compilerData <- CompilerData.from(context, chunk)
      compilationData <- CompilationData.from(sources, context, chunk)
    }
    yield {
      scalaLibraryWarning(modules, compilationData, client)

      val server = getServer(context)
      server.compile(sbtData, compilerData, compilationData, client)
    }
  }

  protected def isDisabled(context: CompileContext): Boolean

  protected def isNeeded(context: CompileContext, chunk: ModuleChunk,
                         dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Boolean

  override def getCompilableFileExtensions: util.List[String] = List("scala").asJava
}

object ScalaBuilder {

  // Invokation of these methods can take a long time on large projects (like IDEA's one)
  def isScalaProject(project: JpsProject): Boolean = hasScalaSdks(project.getModules)

  def hasScalaModules(chunk: ModuleChunk): Boolean = hasScalaSdks(chunk.getModules)

  def hasBuildModules(chunk: ModuleChunk): Boolean = {
    import _root_.scala.collection.JavaConversions._
    chunk.getModules.exists(_.getName.endsWith("-build")) // gen-idea doesn't use the SBT module type
  }

  private def hasScalaSdks(modules: util.Collection[JpsModule]): Boolean = {
    import _root_.scala.collection.JavaConversions._
    modules.exists(SettingsManager.hasScalaSdk)
  }

  def projectSettings(context: CompileContext) = SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)

  val Log = JpsLogger.getInstance(classOf[ScalaBuilder])

  // Cached local localServer
  private var cachedServer: Option[Server] = None

  private val lock = new Object()

  def localServer = {
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
    val pluginRoot = new File(PathManager.getJarPathForClass(getClass)).getParentFile
    val systemRoot = Utils.getSystemRoot
    val javaClassVersion = System.getProperty("java.class.version")

    SbtData.from(classLoader, pluginRoot, systemRoot, javaClassVersion)
  }

  private def scalaLibraryWarning(modules: Set[JpsModule], compilationData: CompilationData, client: Client) {
    val hasScalaFacet = modules.exists(SettingsManager.hasScalaSdk)
    val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

    if (hasScalaFacet && !hasScalaLibrary) {
      val names = modules.map(_.getName).mkString(", ")
      client.warning("No 'scala-library*.jar' in module dependencies [%s]".format(names))
    }
  }

  private def getServer(context: CompileContext): Server = {
    val settings = SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)

    if (settings.isCompileServerEnabled && JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null) {
      cleanLocalServerCache()
      new RemoteServer(InetAddress.getByName(null), settings.getCompileServerPort)
    } else {
      localServer
    }
  }
}
