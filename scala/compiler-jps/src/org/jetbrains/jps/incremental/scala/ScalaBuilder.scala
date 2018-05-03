package org.jetbrains.jps.incremental.scala

import _root_.java.io._
import java.net.InetAddress

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.model.{GlobalSettings, ProjectSettings}
import org.jetbrains.jps.incremental.scala.remote.RemoteServer
import org.jetbrains.jps.model.module.JpsModule

import _root_.scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 11/19/13
 */

object ScalaBuilder {

  def compile(context: CompileContext,
              chunk: ModuleChunk,
              sources: Seq[File],
              allSources: Seq[File],
              modules: Set[JpsModule],
              client: Client): Either[String, ModuleLevelBuilder.ExitCode] = {

    context.processMessage(new ProgressMessage("Reading compilation settings..."))

    for {
      sbtData <-  sbtData
      compilerData <- CompilerData.from(context, chunk)
      compilationData <- CompilationData.from(sources, allSources, context,  chunk)
    }
    yield {
      scalaLibraryWarning(modules, compilationData, client)

      val server = getServer(context)
      server.compile(sbtData, compilerData, compilationData, client)
    }
  }

  def hasBuildModules(chunk: ModuleChunk): Boolean = {
    chunk.getModules.asScala.exists(_.getName.endsWith("-build")) // gen-idea doesn't use the sbt module type
  }

  def projectSettings(context: CompileContext): ProjectSettings = SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)

  val Log: JpsLogger = JpsLogger.getInstance(ScalaBuilder.getClass.getName)

  // Cached local localServer
  private var cachedServer: Option[Server] = None

  private val lock = new Object()

  def localServer: Server = {
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
    val javaClassVersion = System.getProperty("java.class.version")

    SbtData.from(classLoader, pluginRoot, javaClassVersion)
  }

  private def scalaLibraryWarning(modules: Set[JpsModule], compilationData: CompilationData, client: Client) {
    val hasScalaFacet = modules.exists(SettingsManager.hasScalaSdk)
    val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

    val hasScalaSources = compilationData.sources.exists(_.getName.endsWith(".scala"))

    if (hasScalaFacet && !hasScalaLibrary && hasScalaSources) {
      val names = modules.map(_.getName).mkString(", ")
      client.warning("No 'scala-library*.jar' in module dependencies [%s]".format(names))
    }
  }

  private def getServer(context: CompileContext): Server = {
    if (isCompileServerEnabled(context)) {
      cleanLocalServerCache()
      new RemoteServer(InetAddress.getByName(null), globalSettings(context).getCompileServerPort)
    } else {
      localServer
    }
  }

  def isCompileServerEnabled(context: CompileContext): Boolean =
    globalSettings(context).isCompileServerEnabled && isCompilationFromIDEA(context)

  //hack to not run compile server on teamcity; is there a better way?
  private def isCompilationFromIDEA(context: CompileContext): Boolean =
    JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null

  private def globalSettings(context: CompileContext): GlobalSettings =
    SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)
}
