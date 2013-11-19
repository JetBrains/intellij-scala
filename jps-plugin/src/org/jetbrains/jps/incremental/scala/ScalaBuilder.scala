package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.{Utils, CompileContext, ModuleLevelBuilder, BuilderCategory}
import org.jetbrains.jps.ModuleChunk
import java.io.File
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.incremental.scala.data.{SbtData, CompilationData, CompilerData}
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.scala.local.LocalServer
import com.intellij.util.PathUtil
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.scala.remote.RemoteServer
import java.net.InetAddress
import java.util
import collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 11/19/13
 */

abstract class ScalaBuilder(builderCategory: BuilderCategory) extends ModuleLevelBuilder(builderCategory) {

  def compile(context: CompileContext,
              chunk: ModuleChunk,
              sources: Seq[File],
              modules: Set[JpsModule],
              client: Client): Either[String, ModuleLevelBuilder.ExitCode] = {
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

  private def scalaLibraryWarning(modules: Set[JpsModule], compilationData: CompilationData, client: Client) {
    val hasScalaFacet = modules.exists(SettingsManager.getFacetSettings(_) != null)
    val hasScalaLibrary = compilationData.classpath.exists(_.getName.startsWith("scala-library"))

    if (hasScalaFacet && !hasScalaLibrary) {
      val names = modules.map(_.getName).mkString(", ")
      client.warning("No 'scala-library*.jar' in module dependencies [%s]".format(names))
    }
  }

  private def getServer(context: CompileContext): Server = {
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
}

object ScalaBuilder {
  val Log = JpsLogger.getInstance(classOf[SbtBuilder])

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
