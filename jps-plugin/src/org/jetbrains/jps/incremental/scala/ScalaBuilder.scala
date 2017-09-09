package org.jetbrains.jps.incremental.scala

import _root_.java.io._
import java.net.InetAddress
import java.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.model.{IncrementalityType, ProjectSettings}
import org.jetbrains.jps.incremental.scala.remote.RemoteServer
import org.jetbrains.jps.model.JpsProject
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

  def checkIncrementalTypeChange(context: CompileContext): Unit = {
    def storageFile: Option[File] = {
      val projectDir = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
      if (projectDir != null)
        Some(new File(projectDir, "incrementalType.dat"))
      else None
    }

    def getPreviousIncrementalType: Option[IncrementalityType] = {
      storageFile.filter(_.exists).flatMap { file =>
        val result = using(new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) { in =>
          try {
            Some(IncrementalityType.valueOf(in.readUTF()))
          } catch {
            case _: IOException | _: IllegalArgumentException | _: NullPointerException => None
          }
        }
        if (result.isEmpty) file.delete()
        result
      }
    }

    def setPreviousIncrementalType(incrType: IncrementalityType) {
      storageFile.foreach { file =>
        val parentDir = file.getParentFile
        if (!parentDir.exists()) parentDir.mkdirs()
        using(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
          _.writeUTF(incrType.name)
        }
      }
    }

    def cleanCaches() {
      context.getProjectDescriptor.setFSCache(FSCache.NO_CACHE)
      try {
        val directory = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
        FileUtil.delete(directory)
      }
      catch {
        case e: Exception => throw new IOException("Can not delete project system directory: \n" + e.getMessage)
      }
    }

    val settings = projectSettings(context)
    val previousIncrementalType = getPreviousIncrementalType
    val incrType = settings.getIncrementalityType
    previousIncrementalType match {
      case _ if JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) => //isRebiuld
        setPreviousIncrementalType(incrType)
      case None =>
      //        ScalaBuilderDelegate.Log.info("scala: cannot find type of the previous incremental compiler, full rebuild may be required")
      case Some(`incrType`) => //same incremental type, nothing to be done
      case Some(_) if isMakeProject(context) =>
        if (ScalaBuilder.isScalaProject(context.getProjectDescriptor.getProject)) {
          cleanCaches()
          setPreviousIncrementalType(incrType)
          context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING,
            "type of incremental compiler has been changed, full rebuild..."))
        }
      case Some(_) =>
        if (ScalaBuilder.isScalaProject(context.getProjectDescriptor.getProject)) {
          throw new ProjectBuildException("scala: type of incremental compiler has been changed, full rebuild is required")
        }
    }
  }

  // Invokation of these methods can take a long time on large projects (like IDEA's one)
  def isScalaProject(project: JpsProject): Boolean = hasScalaSdks(project.getModules)
  def hasScalaModules(chunk: ModuleChunk): Boolean = hasScalaSdks(chunk.getModules)
  private def hasScalaSdks(modules: util.Collection[JpsModule]): Boolean = {
    modules.asScala.exists(SettingsManager.hasScalaSdk)
  }

  def hasBuildModules(chunk: ModuleChunk): Boolean = {
    chunk.getModules.asScala.exists(_.getName.endsWith("-build")) // gen-idea doesn't use the SBT module type
  }

  def projectSettings(context: CompileContext): ProjectSettings = SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)

  def isMakeProject(context: CompileContext): Boolean = JavaBuilderUtil.isCompileJavaIncrementally(context) && {
    for {
      chunk <- context.getProjectDescriptor.getBuildTargetIndex.getSortedTargetChunks(context).asScala
      target <- chunk.getTargets.asScala
    } {
      if (!context.getScope.isAffected(target)) return false
    }
    true
  }

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
    val settings = SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)

    if (settings.isCompileServerEnabled && JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null) {
      cleanLocalServerCache()
      new RemoteServer(InetAddress.getByName(null), settings.getCompileServerPort)
    } else {
      localServer
    }
  }
}
