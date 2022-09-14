package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.compiler.server.{BuildManagerListener, BuildProcessParametersProvider}
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.{ApplicationManager, PathManagerEx}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.net.NetUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.server.{CompileServerProperties, CompileServerToken}
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util._
//noinspection ApiStatus,UnstableApiUsage
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils

import java.io.{BufferedReader, File, IOException, InputStreamReader}
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.swing.event.HyperlinkEvent
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.Exception._

object CompileServerLauncher {

  @volatile private var serverInstance: Option[ServerInstance] = None
  private val LOG = Logger.getInstance(getClass)

  private val NailgunRunnerFQN = "org.jetbrains.plugins.scala.nailgun.NailgunRunner"

  private def attachDebugAgent = false
  private def waitUntilDebuggerAttached = true
  private def debugAgentPort = "5006"

  /* @see [[org.jetbrains.plugins.scala.compiler.ServerMediatorTask]] */
  private class Listener extends BuildManagerListener {

    override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
      if (!project.isDisposed)
        ensureCompileServerRunning(project)
      if (ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED)
        CompileServerNotificationsService.get(project).warnIfCompileServerJdkMayLeadToCompilationProblems()
    }

    private def ensureCompileServerRunning(project: Project): Unit = {
      val settings = ScalaCompileServerSettings.getInstance

      val compileServerRequired = settings.COMPILE_SERVER_ENABLED && project.hasScala
      LOG.traceWithDebugInDev(s"Listener.compileServerRequired: $compileServerRequired")
      if (compileServerRequired) {
        CompileServerLauncher.ensureServerRunning(project)
      }
    }
  }

  ScalaShutDownTracker.registerShutdownTask(() => {
    LOG.info("Shutdown event triggered, stopping server")
    ensureServerNotRunning()
  })

  def tryToStart(project: Project): Boolean = serverStartLock.synchronized {
    if (running) true
    else start(project)
  }

  private def isUnitTestMode: Boolean =
    ApplicationManager.getApplication.isUnitTestMode

  private def start(project: Project): Boolean = {
    val result = for {
      jdk     <- compileServerJdk(project)
      process <- start(project, jdk)
    } yield process

    result match {
      case Right(_) =>
        CompileServerNotificationsService.get(project).resetNotifications()
        true
      case Left(error)  =>
        val title = CompilerIntegrationBundle.message("cannot.start.scala.compile.server")
        val groupId = "scala"
        error match {
          case CompileServerProblem.SdkNotSpecified =>
            val text =
              s"""No SDK specified.<p/>
                 |""".stripMargin
            val action = new OpenScalaCompileServerSettingsAction(project, filter = "JDK")
            Notifications.Bus.notify(new Notification(groupId, title, text, NotificationType.ERROR).addAction(action))
          case error: CompileServerProblem.Error =>
            val text = error.text
            Notifications.Bus.notify(new Notification(groupId, title, text, NotificationType.ERROR))
            LOG.error(title, text)
          case error: CompileServerProblem.UnexpectedException =>
            val text = error.cause.getMessage
            Notifications.Bus.notify(new Notification(groupId, title, text, NotificationType.ERROR))
            LOG.error(title, error.cause)
        }
        false
    }
  }

  private def compilerServerAdditionalCP(): Iterable[File] = for {
    extension        <- CompileServerClasspathProvider.implementations
    pluginDescriptor = extension.getPluginDescriptor
    pluginsLibs      = new File(pluginDescriptor.getPluginPath.toFile, "lib")
    filesPath        <- extension.classpathSeq
  } yield new File(pluginsLibs, filesPath)

  // TODO: track that we attach debug agent and show notification, as with JPS Build Process
  // TODO: add internal action "Debug Scala Compile Server" as with JPS "Debug Build Process"
  private def start(project: Project, jdk: JDK): Either[CompileServerProblem, Process] = {
    LOG.traceWithDebugInDev(s"starting server")

    val settings = ScalaCompileServerSettings.getInstance

    settings.COMPILE_SERVER_SDK = jdk.name
    saveSettings()

    compileServerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
        val (nailgunCpFiles, classpathFiles) = presentFiles.partition(_.getName contains "nailgun")
        val nailgunClasspath = nailgunCpFiles
          .map(_.canonicalPath).mkString(File.pathSeparator)
        val buildProcessClasspath = {
          //noinspection ApiStatus
          // in worksheet tests we reuse compile server between projects
          // we initialize it before the first test starts, so the project is `null`
          // TODO: make project "Option"
          val pluginsClasspath = if (isUnitTestMode && (project eq null) || project.isDisposed) Seq() else
            new BuildProcessClasspathManager(project.unloadAwareDisposable).getBuildProcessPluginsClasspath(project).asScala
          val applicationClasspath = ClasspathBootstrap.getBuildProcessApplicationClasspath.asScala
          pluginsClasspath ++ applicationClasspath
        }
        val classpath =
          (jdk.tools ++ (classpathFiles ++ compilerServerAdditionalCP()))
            .map(_.canonicalPath) ++ buildProcessClasspath

        val freePort = CompileServerLauncher.findFreePort
        if (settings.COMPILE_SERVER_PORT != freePort) {
          new RemoteServerStopper(settings.COMPILE_SERVER_PORT).sendStop()
          settings.COMPILE_SERVER_PORT = freePort
          saveSettings()
        }
        deleteOldTokenFile(scalaCompileServerSystemDir, freePort)
        val id = settings.COMPILE_SERVER_ID

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY * 60
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay.seconds=$shutdownDelay")
        } else Nil
        val isScalaCompileServer = s"-D${CompileServerProperties.IsScalaCompileServer}=true"

        val vmOptions: Seq[String] = if (isUnitTestMode && project == null) Seq() else {
          val buildProcessParameters = BuildProcessParametersProvider.EP_NAME.getExtensions(project).asScala.iterator
            .flatMap(_.getVMArguments.asScala).toSeq
          val extraJvmParameters = CompileServerVmOptionsProvider.implementations.iterator
            .flatMap(_.vmOptionsFor(project)).toSeq
          //see SCL-20064
          val workaroundForSecurityManagerForJDK18 =
            if (jdk.version.exists(_.isAtLeast(JavaSdkVersion.JDK_18)))
              Seq("-Djava.security.manager=allow")
            else Nil
          buildProcessParameters ++ extraJvmParameters ++ workaroundForSecurityManagerForJDK18
        }

        // SCL-18193
        val addOpensOptions = if (jdk.version.exists(_ isAtLeast JavaSdkVersion.JDK_1_9))
          createJvmAddOpensParams(
            "java.base/java.nio",
            "java.base/java.util",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.compiler/com.sun.tools.javac.file",
            "jdk.compiler/com.sun.tools.javac.util",
            "jdk.compiler/com.sun.tools.javac.main",
            "java.base/sun.nio.ch"
          )
        else
          Seq.empty

        val userJvmParameters = jvmParameters
        val commands =
          jdk.executable.canonicalPath +:
            "-cp" +: nailgunClasspath +:
            userJvmParameters ++:
            shutdownDelayArg ++:
            isScalaCompileServer +:
            addOpensOptions ++:
            vmOptions ++:
            NailgunRunnerFQN +:
            freePort.toString +:
            id +:
            classpath.mkString(File.pathSeparator) +:
            scalaCompileServerSystemDir.toFile.getCanonicalPath +:
            Nil

        val builder = new ProcessBuilder(commands.asJava)

        if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
          projectHome(project).foreach(dir => builder.directory(dir))
        }

        catching(classOf[IOException])
          .either(builder.start())
          .left.map(e => CompileServerProblem.UnexpectedException(e))
          .map { process =>
            val bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))
            var cont = true
            while (cont) {
              val line = bufferedReader.readLine()
              if (line eq null) {
                // Reached the end of the stream. The process listener will take care of the rest.
                cont = false
              } else if (line.startsWith("NGServer") && line.contains("started on") && line.contains(freePort.toString)) {
                // The NGServer is ready to accept connections.
                cont = false
              }
            }

            val watcher = new ProcessWatcher(project, process, "scalaCompileServer")
            val instance = new ServerInstance(watcher, freePort, builder.directory(), jdk, userJvmParameters.toSet)
            LOG.assertTrue(serverInstance.isEmpty, "serverInstance is expected to be None")
            serverInstance = Some(instance)
            // initialize the compile server manager service instance for the project which holds the widget state
            CompileServerManager.init(project)
            project.getMessageBus.syncPublisher(CompileServerManager.ServerStatusTopic).onServerStatus(true)
            watcher.startNotify()
            watcher.addProcessListener(new ProcessAdapter {
              override def processTerminated(event: ProcessEvent): Unit = {
                // CS can terminate if we close IDEA and the project will be disposed already
                if (!project.isDisposed) {
                  val isExpectedProcessTermination = watcher.isTerminatedByIdleTimeout || instance.stopped
                  if (!isExpectedProcessTermination) {
                    invokeLater {
                      CompileServerManager(project).showNotification(CompilerIntegrationBundle.message("compile.server.terminated.unexpectedly.0.port.1.pid", instance.port, instance.pid), NotificationType.WARNING)
                      LOG.warn(s"Compile server terminated unexpectedly: ${instance.summary}")
                    }
                  }
                }

                serverInstance = None
                if (!project.isDisposed) {
                  project.getMessageBus.syncPublisher(CompileServerManager.ServerStatusTopic).onServerStatus(false)
                }
              }
            })
            infoAndPrintOnTeamcity(s"compile server process started: ${instance.summary}")
            LOG.debug(s"command line: ${builder.command().asScala.mkString(" ")}")
            LOG.debug(s"working directory: ${instance.workingDir}")

            if (attachDebugAgent) {
              // this line, printed to the stdout of dev IDEA instance will cause debugger
              // to automatically attach to the process in main IDEA instance
              // (works only if `debugger.auto.attach.from.console` registry is enabled in main IDEA instance)
              LOG.info(s"Listening for transport dt_socket at address: $debugAgentPort")
            }
            process
          }
      case (_, absentFiles) =>
        val paths = absentFiles.map(_.getPath).mkString(", ")
        Left(CompileServerProblem.Error(CompilerIntegrationBundle.message("required.file.not.found.paths", paths)))
    }
  }

  // ensure that old tokens from old sessions do not exist on file system to avoid race conditions (see ticket from the commit)
  // it should be deleted in org.jetbrains.plugins.scala.nailgun.NailgunRunner.ShutdownHook.run
  // but in case of some server crashes it can remain on the file system
  private def deleteOldTokenFile(scalaCompileServerSystemDir: Path, freePort: Int): Unit =
    Try(Files.delete(CompileServerToken.tokenPathForPort(scalaCompileServerSystemDir, freePort)))

  // TODO stop server more gracefully
  def stop(timeoutMs: Long = 0, debugReason: Option[String] = None): Boolean = {
    LOG.info(s"compile server process stop: ${serverInstance.map(_.summary).getOrElse("<no info>")}")
    serverInstance.forall { it =>
      val bool = it.destroyAndWait(timeoutMs)
      infoAndPrintOnTeamcity(s"compile server process stopped${debugReason.fold("")(", reason: " + _)}")
      bool
    }
  }

  private def infoAndPrintOnTeamcity(message: String): Unit = {
    LOG.info(message)
    TeamcityUtils.logUnderTeamcity(message)
  }

  def running: Boolean = serverInstance.exists(_.running)

  def port: Option[Int] = serverInstance.map(_.port)
  def pid: Option[Long] = serverInstance.map(_.watcher.pid)

  def defaultSdk(project: Project): Sdk =
    CompileServerJdkManager.recommendedSdk(project)
      .getOrElse(CompileServerJdkManager.getBuildProcessRuntimeSdk(project))

  def compileServerSdk(project: Project): Either[CompileServerProblem, Sdk] = {
    val settings = ScalaCompileServerSettings.getInstance()

    val sdk =
      if (settings.USE_DEFAULT_SDK)
        Option(defaultSdk(project))
          .toRight(CompileServerProblem.Error(CompilerIntegrationBundle.message("can.t.find.default.jdk")))
      else if (settings.COMPILE_SERVER_SDK != null)
        Option(ProjectJdkTable.getInstance().findJdk(settings.COMPILE_SERVER_SDK))
          .toRight(CompileServerProblem.Error(CompilerIntegrationBundle.message("cant.find.jdk", settings.COMPILE_SERVER_SDK)))
      else
        Left(CompileServerProblem.SdkNotSpecified)
    sdk
  }

  def compileServerJdk(project: Project): Either[CompileServerProblem, JDK] = {
    val sdk = compileServerSdk(project)
    sdk.flatMap(toJdk)
  }

  def compileServerJars: Seq[File] = Seq(
    IntellijPlatformJars.jpsBuildersJar,
    IntellijPlatformJars.utilJar,
    IntellijPlatformJars.utilRtJar,
    IntellijPlatformJars.trove4jJar,
    IntellijPlatformJars.protobufJava, // required for org.jetbrains.jps.incremental.scala.remote.Main.compileJpsLogic
    IntellijPlatformJars.fastUtilJar,
    LibraryJars.scalaParserCombinators,
    ScalaPluginJars.scalaLibraryJar,
    ScalaPluginJars.scalaReflectJar,
    ScalaPluginJars.scalaNailgunRunnerJar,
    ScalaPluginJars.compilerSharedJar,
    ScalaPluginJars.nailgunJar,
    ScalaPluginJars.sbtInterfaceJar,
    ScalaPluginJars.incrementalCompilerJar,
    ScalaPluginJars.compilerJpsJar,
    ScalaPluginJars.replInterface,
  ).distinct

  def jvmParameters: Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance
    val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
      if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
    }

    val paramsParsed = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").filter(StringUtils.isNotBlank)
    val (_, otherParams) = paramsParsed.partition(_.contains("-XX:MaxPermSize"))

    val debugAgent: Option[String] =
      if (attachDebugAgent) {
        val suspend = if(waitUntilDebuggerAttached) "y" else "n"
        Some(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=$debugAgentPort")
      } else None

    xmx ++ otherParams ++ debugAgent
  }

  private val serverStartLock = new Object

  // TODO: make it thread safe, call from a single thread OR use some locking mechanism

  def ensureServerRunning(project: Project): Boolean = serverStartLock.synchronized {
    LOG.traceWithDebugInDev(s"ensureServerRunning [thread:${Thread.currentThread.getId}]")
    val reasons = restartReasons(project)
    if (reasons.nonEmpty) {
      val stopped = stop(timeoutMs = 3000L, debugReason = Some(s"needs to restart: ${reasons.mkString(", ")}"))
      if (!stopped && ApplicationManager.getApplication.isUnitTestMode) {
        LOG.error("couldn't stop compile server")
      }
    }

    running || tryToStart(project)
  }

  private def restartReasons(project: Project): Seq[String] = {
    val currentInstance = serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    currentInstance.map { instance =>
      val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
      val workingDirChanged = useProjectHome && projectHome(project) != currentInstance.map(_.workingDir)
      val jdkChanged = compileServerJdk(project) match {
        case Right(projectJdk) => projectJdk != instance.jdk
        case _ => false
      }
      val jvmParametersChanged = jvmParameters.toSet != instance.jvmParameters

      val reasons = mutable.ArrayBuffer.empty[String]
      if (workingDirChanged) reasons += "working dir changed"
      if (jdkChanged) reasons += "jdk changed"
      if (jvmParametersChanged) reasons += "jvm parameters changed"
      reasons.toSeq
    }.getOrElse(Seq.empty)
  }

  def ensureServerNotRunning(): Unit = serverStartLock.synchronized {
    if (running) stop(debugReason = Some("ensureServerNotRunning"))
  }

  private def findFreePort: Int = {
    val port = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_PORT
    if (!isUsed(port)) port else {
      LOG.info(s"compile server port is already used ($port), searching for available port")
      val result = NetUtils.findAvailableSocketPort()
      LOG.info(s"found available port: $result")
      result
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

  sealed trait CompileServerProblem

  object CompileServerProblem {
    final case object SdkNotSpecified extends CompileServerProblem
    final case class Error(@Nls text: String) extends CompileServerProblem
    final case class UnexpectedException(cause: Throwable) extends CompileServerProblem
  }

  def scalaCompileServerSystemDir: Path =
    PathManagerEx.getAppSystemDir.resolve("scala-compile-server")
}
