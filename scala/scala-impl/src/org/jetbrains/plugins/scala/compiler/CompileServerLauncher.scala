package org.jetbrains.plugins.scala
package compiler

import java.io.{File, IOException}
import java.nio.file.Files
import java.util.UUID

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.compiler.server.{BuildManager, BuildManagerListener, BuildProcessParametersProvider}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.{ShutDownTracker, SimpleModificationTracker}
import com.intellij.util.net.NetUtils
import javax.swing.event.HyperlinkEvent
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.server.{CompileServerProperties, CompileServerToken}
import org.jetbrains.plugins.scala.util.{IntellijPlatformJars, LibraryJars, ScalaPluginJars}

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.Exception._

object CompileServerLauncher {

  private var serverInstance: Option[ServerInstance] = None
  private val LOG = Logger.getInstance(getClass)

  private val NailgunRunnerFQN = "org.jetbrains.plugins.scala.nailgun.NailgunRunner"

  private def attachDebugAgent = false
  private def waitUntilDebuggerAttached = false

  /* @see [[org.jetbrains.plugins.scala.compiler.ServerMediatorTask]] */
  private class Listener extends BuildManagerListener {

    override def beforeBuildProcessStarted(project: Project, sessionId: UUID): Unit = {
      ensureCompileServerRunning(project)
      if (ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED)
        CompileServerNotificationsService.get(project).warnIfCompileServerJdkVersionTooOld()
    }

    override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
      ensureCompileServerRunning(project)

    private def ensureCompileServerRunning(project: Project): Unit = {
      val settings = ScalaCompileServerSettings.getInstance

      val compileServerRequired = settings.COMPILE_SERVER_ENABLED && project.hasScala
      LOG.traceSafe(s"Listener.compileServerRequired: $compileServerRequired")
      if (compileServerRequired) {
        invokeAndWait {
          CompileServerManager.configureWidget(project)
        }
        CompileServerLauncher.ensureServerRunning(project)
      }
    }
  }

  ShutDownTracker.getInstance().registerShutdownTask { () =>
    ensureServerNotRunning()
  }

  def tryToStart(project: Project): Boolean = serverStartLock.synchronized {
    if (running) true else {
      val started = start(project)
      if (started) {
        sendDummyRequest(project)
      }
      started
    }
  }

  // TODO: implement proper wait for server initialization, addDisconnectListener command doesn't even exist
  //  Nailgun server sends error for it via stderr which we ignore by passing null client
  private def sendDummyRequest(project: Project): Try[Unit] =
    Try {
      new RemoteServerRunner(project).send("addDisconnectListener", Seq.empty, null)
    }.recoverWith { case _: Exception if isUnitTestMode && waitUntilDebuggerAttached =>
      LOG.traceSafe("waiting for compile server initialization...")
      sendDummyRequest(project)
    }


  private def isUnitTestMode: Boolean =
    ApplicationManager.getApplication.isUnitTestMode

  private def start(project: Project): Boolean = {
    val result = for {
      jdk     <- compileServerJdk(project).left.map(m => s"JDK for compiler process not found: $m")
      process <- start(project, jdk)
    } yield process

    result match {
      case Right(_) =>
        invokeLater {
          CompileServerManager.configureWidget(project)
        }
        CompileServerNotificationsService.get(project).resetNotifications()
        true
      case Left(error)  =>
        val title = ScalaBundle.message("cannot.start.scala.compile.server")
        Notifications.Bus.notify(new Notification("scala", title, error, NotificationType.ERROR))
        LOG.error(title, error)
        false
    }
  }

  private def compilerServerAdditionalCP(): Seq[File] = for {
    extension        <- CompileServerClasspathProvider.implementations
    pluginDescriptor = extension.getPluginDescriptor
    pluginsLibs      = new File(pluginDescriptor.getPluginPath.toFile, "lib")
    filesPath        <- extension.classpathSeq
  } yield new File(pluginsLibs, filesPath)

  private def start(project: Project, jdk: JDK): Either[String, Process] = {
    LOG.traceSafe(s"starting server")

    val settings = ScalaCompileServerSettings.getInstance

    settings.COMPILE_SERVER_SDK = jdk.name
    saveSettings()

    compileServerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
        val (nailgunCpFiles, classpathFiles) = presentFiles.partition(_.getName contains "nailgun")
        val nailgunClasspath = nailgunCpFiles
          .map(_.canonicalPath).mkString(File.pathSeparator)
        val buildProcessClasspath = {
          // in worksheet tests we reuse compile server between project
          // so we initialize it before the first test starts
          val pluginsClasspath = if (isUnitTestMode && project == null) Seq() else
            new BuildProcessClasspathManager(project.unloadAwareDisposable).getBuildProcessPluginsClasspath(project).asScala
          val applicationClasspath = ClasspathBootstrap.getBuildProcessApplicationClasspath.asScala
          pluginsClasspath ++ applicationClasspath
        }
        val classpath = ((jdk.tools ++ (classpathFiles ++ compilerServerAdditionalCP()))
          .map(_.canonicalPath) ++ buildProcessClasspath)

        val freePort = CompileServerLauncher.findFreePort
        if (settings.COMPILE_SERVER_PORT != freePort) {
          new RemoteServerStopper(settings.COMPILE_SERVER_PORT).sendStop()
          settings.COMPILE_SERVER_PORT = freePort
          saveSettings()
        }
        deleteOldTokenFile(freePort)
        val id = settings.COMPILE_SERVER_ID

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay=$shutdownDelay")
        } else Nil
        val isScalaCompileServer = s"-D${CompileServerProperties.IsScalaCompileServer}=true"

        val vmOptions: Seq[String] = if (isUnitTestMode && project == null) Seq() else {
          val buildProcessParameters = BuildProcessParametersProvider.EP_NAME.getExtensions(project).asScala
            .flatMap(_.getVMArguments.asScala)
          val extraJvmParameters = CompileServerVmOptionsProvider.implementations
            .flatMap(_.vmOptionsFor(project))
          buildProcessParameters ++ extraJvmParameters
        }

        val commands =
          jdk.executable.canonicalPath +:
            "-cp" +: nailgunClasspath +:
            jvmParameters ++:
            shutdownDelayArg ++:
            isScalaCompileServer +:
            vmOptions ++:
            NailgunRunnerFQN +:
            freePort.toString +:
            id +:
            classpath.mkString(File.pathSeparator) +:
            Nil

        val builder = new ProcessBuilder(commands.asJava)

        if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
          projectHome(project).foreach(dir => builder.directory(dir))
        }

        catching(classOf[IOException])
          .either(builder.start())
          .left.map(_.getMessage)
          .map { process =>
            val watcher = new ProcessWatcher(process, "scalaCompileServer")
            val instance = ServerInstance(watcher, freePort, builder.directory(), jdk)
            serverInstance = Some(instance)
            watcher.startNotify()
            LOG.info(s"compile server process started: ${instance.summary}")
            process
          }
      case (_, absentFiles) =>
        val paths = absentFiles.map(_.getPath).mkString(", ")
        Left("Required file(s) not found: " + paths)
    }
  }

  // ensure that old tokens from old sessions do not exist on file system to avoid race conditions (see ticket from the commit)
  // it should be deleted in org.jetbrains.plugins.scala.nailgun.NailgunRunner.ShutdownHook.run
  // but in case of some server crashes it can remain on the file system
  private def deleteOldTokenFile(freePort: Int): Unit =
    Try(Files.delete(CompileServerToken.tokenPathForPort(freePort)))

  // TODO stop server more gracefully
  def stop(timeoutMs: Long = 0): Boolean = {
    LOG.traceSafe(s"stop: ${serverInstance.map(_.summary)}")
    serverInstance.forall { it =>
      it.destroyAndWait(timeoutMs)
    }
  }

  def stop(project: Project): Unit = {
    stop()

    invokeLater {
      CompileServerManager.configureWidget(project)
    }
  }

  def running: Boolean = serverInstance.exists(_.running)

  def errors(): Seq[String] = serverInstance.map(_.errors()).getOrElse(Seq.empty)

  def port: Option[Int] = serverInstance.map(_.port)
  
  def defaultSdk(project: Project): Sdk =
    CompileServerJdkManager.recommendedSdk(project)
      .getOrElse(getBuildProcessRuntimeSdk(project))

  /**
   * Returns the Build Process runtime SDK.
   * The method isn't thread-safe, so the synchronized is used.
   * @see SCL-17710
   */
  private def getBuildProcessRuntimeSdk(project: Project): Sdk = synchronized {
    BuildManager.getBuildProcessRuntimeSdk(project).first
  }

  def compileServerSdk(project: Project): Either[String, Sdk] = {
    val settings = ScalaCompileServerSettings.getInstance()

    val sdk =
      if (settings.USE_DEFAULT_SDK) Option(defaultSdk(project)).toRight("can't find default jdk")
      else Option(ProjectJdkTable.getInstance().findJdk(settings.COMPILE_SERVER_SDK)).toRight(s"can't find jdk: ${settings.COMPILE_SERVER_SDK}")

    sdk
  }

  def compileServerJdk(project: Project): Either[String, JDK] = {
    val sdk = compileServerSdk(project)
    sdk.flatMap(toJdk)
  }

  def compileServerJars: Seq[File] = Seq(
    IntellijPlatformJars.jpsBuildersJar,
    IntellijPlatformJars.utilJar,
    IntellijPlatformJars.trove4jJar,
    IntellijPlatformJars.protobufJava,
    LibraryJars.scalaParserCombinators,
    ScalaPluginJars.scalaLibraryJar,
    ScalaPluginJars.scalaReflectJar,
    ScalaPluginJars.scalaNailgunRunnerJar,
    ScalaPluginJars.compilerSharedJar,
    ScalaPluginJars.nailgunJar,
    ScalaPluginJars.sbtInterfaceJar,
    ScalaPluginJars.incrementalCompilerJar,
    ScalaPluginJars.compilerJpsJar,
  )

  def jvmParameters: Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance
    val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
      if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
    }

    val (_, otherParams) = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").partition(_.contains("-XX:MaxPermSize"))

    val debugAgent: Option[String] =
      if (attachDebugAgent) {
        val suspend = if(waitUntilDebuggerAttached) "y" else "n"
        Some(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=5006")
      } else None

    xmx ++ otherParams ++ debugAgent
  }

  private val serverStartLock = new Object

  // TODO: make it thread safe, call from a single thread OR use some locking mechanism

  def ensureServerRunning(project: Project): Boolean = serverStartLock.synchronized {
    LOG.traceSafe(s"ensureServerRunning [thread:${Thread.currentThread.getId}]")
    if (needRestart(project)) {
      LOG.traceSafe("ensureServerRunning: need to restart, stopping")
      stop()
    }

    running || tryToStart(project)
  }

  private def needRestart(project: Project): Boolean = {
    val currentInstance = serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    currentInstance match {
      case None => false // if no server running, then nothing to restart
      case Some(instance) =>
        val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
        val workingDirChanged = useProjectHome && projectHome(project) != currentInstance.map(_.workingDir)
        val jdkChanged = compileServerJdk(project) match {
          case Right(projectJdk) => projectJdk != instance.jdk
          case _ => false
        }
        workingDirChanged || jdkChanged
    }
  }

  def ensureServerNotRunning(project: Project): Unit = serverStartLock.synchronized {
    if (running) stop(project)
  }

  private def ensureServerNotRunning(): Unit = serverStartLock.synchronized {
    if (running) stop()
  }

  private def findFreePort: Int = {
    val port = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_PORT
    if (!isUsed(port)) port else {
      LOG.info(s"compile server port is already used ($port), searching for available port")
      NetUtils.findAvailableSocketPort()
    }
  }

  private def isUsed(portFromSettings: Int): Boolean =
    NetUtils.canConnectToSocket("localhost", portFromSettings)

  private def saveSettings(): Unit = invokeAndWait {
    ApplicationManager.getApplication.saveSettings()
  }

  private def projectHome(project: Project): Option[File] = {
    for {
      dir <- Option(project.baseDir)
      path <- Option(dir.getCanonicalPath)
      file = new File(path)
      if file.exists()
    } yield file
  }


  class ConfigureLinkListener(project: Project) extends NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, event: HyperlinkEvent): Unit = {
      CompileServerManager.showCompileServerSettingsDialog(project)
      notification.expire()
    }
  }

  class ConfigureJDKListener(project: Project) extends NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, event: HyperlinkEvent): Unit = {
      val jdkEntries = project.modulesWithScala.flatMap { module =>
        val rootManager = ModuleRootManager.getInstance(module)
        Option(OrderEntryUtil.findJdkOrderEntry(rootManager, rootManager.getSdk))
      }
      val service = ProjectSettingsService.getInstance(project)

      if (jdkEntries.isEmpty) service.openProjectSettings()
      else service.openLibraryOrSdkSettings(jdkEntries.head)

      notification.expire()
    }
  }
}

private case class ServerInstance(watcher: ProcessWatcher,
                                  port: Int,
                                  workingDir: File,
                                  jdk: JDK) {
  private var stopped = false

  def running: Boolean = !stopped && watcher.running

  def errors(): Seq[String] = watcher.errors()

  def destroyAndWait(timeoutMs: Long): Boolean = {
    stopped = true
    watcher.destroyAndWait(timeoutMs)
  }

  def summary: String = {
    s"port: $port" +
      s", jdk: $jdk" +
      s", stopped: $stopped" +
      s", running: $running" +
      s", errors: ${errors().mkString("(", ", ", ")")}"
  }
}
