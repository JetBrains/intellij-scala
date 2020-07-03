package org.jetbrains.jps
package incremental
package scala

import _root_.java.io._
import _root_.java.net.InetAddress
import _root_.java.util.ServiceLoader

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, SbtData}
import org.jetbrains.plugins.scala.server.CompileServerProperties

import _root_.scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 11/19/13
 */
// TODO: use a proper naming. Scala builder of what? Strings? Code? Psi trees?
object ScalaBuilder {

  import data._

  def compile(context: CompileContext,
              chunk: ModuleChunk,
              sources: Seq[File],
              allSources: Seq[File],
              modules: Set[JpsModule],
              client: Client): Either[String, ModuleLevelBuilder.ExitCode] = {

    context.processMessage(new ProgressMessage("Reading compilation settings..."))

    for {
      sbtData         <-  sbtData
      dataFactory     = dataFactoryOf(context)
      compilerData    <- dataFactory.getCompilerDataFactory.from(context, chunk)
      compilationData <- dataFactory.getCompilationDataFactory.from(sources, allSources, context,  chunk)
    } yield {
      scalaLibraryWarning(modules, compilationData, client)

      val server = getServer(context)
      server.compile(sbtData, compilerData, compilationData, client)
    }
  }

  private def dataFactoryOf(context: CompileContext): DataFactoryService = {
    val df = ServiceLoader.load(classOf[DataFactoryService])
    val registeredDataFactories = df.iterator().asScala.toList
    Log.info(s"Registered factories of ${classOf[DataFactoryService].getName}: $registeredDataFactories")
    val firstEnabledDataFactory = registeredDataFactories.find(_.isEnabled(context.getProjectDescriptor.getProject))
    Log.info(s"First enabled factory (if any): $firstEnabledDataFactory")
    firstEnabledDataFactory.getOrElse(DefaultDataFactoryService)
  }

  def hasBuildModules(chunk: ModuleChunk): Boolean = {
    chunk.getModules.asScala.exists(_.getName.endsWith("-build")) // gen-idea doesn't use the sbt module type
  }

  def projectSettings(context: CompileContext): model.ProjectSettings =
    SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)

  val Log: JpsLogger = JpsLogger.getInstance(ScalaBuilder.getClass.getName)

  // Cached local localServer
  private var cachedServer: Option[Server] = None

  private val lock = new Object()

  def localServer: Server = {
    lock.synchronized {
      val server = cachedServer.getOrElse(new local.LocalServer())
      cachedServer = Some(server)
      server
    }
  }

  private def cleanLocalServerCache(): Unit =
    lock.synchronized {
      cachedServer = None
    }

  private lazy val sbtData = {
    val pluginJpsRoot = new File(PathManager.getJarPathForClass(getClass)).getParentFile
    val javaClassVersion = System.getProperty("java.class.version")
    SbtData.from(pluginJpsRoot, javaClassVersion)
  }

  private def scalaLibraryWarning(modules: Set[JpsModule], compilationData: CompilationData, client: Client): Unit = {
    val hasScalaFacet = modules.exists(SettingsManager.getScalaSdk(_).isDefined)
    val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

    val hasScalaSources = compilationData.sources.exists(_.getName.endsWith(".scala"))

    if (hasScalaFacet && !hasScalaLibrary && hasScalaSources) {
      val names = modules.map(_.getName).mkString(", ")
      client.warning(JpsBundle.message("no.scala.library.jar.in.module.dependencies", names))
    }
  }

  private def getServer(implicit context: CompileContext): Server = {
    if (isCompileServerEnabled && !CompileServerProperties.isScalaCompileServer) {
      cleanLocalServerCache()
      val port = globalSettings.getCompileServerPort
      Log.info(s"using remote server with port: $port")
      new remote.RemoteServer(InetAddress.getByName(null), port)
    } else {
      Log.info("using local server")
      localServer
    }
  }

  def isCompileServerEnabled(implicit context: CompileContext): Boolean =
    globalSettings.isCompileServerEnabled

  private def globalSettings(implicit context: CompileContext) =
    SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)
}
