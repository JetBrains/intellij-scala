package org.jetbrains.plugins.scala
package compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.compiler.server.{BuildManager, BuildManagerListener, BuildProcessParametersProvider}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.net.NetUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.server.{CompileServerProperties, CompileServerToken}
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils
import org.jetbrains.plugins.scala.util.{IntellijPlatformJars, JvmOptions, LibraryJars, ScalaPluginJars, ScalaShutDownTracker}

import java.io.{File, IOException}
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.swing.event.HyperlinkEvent
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._
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
        invokeAndWait {
          CompileServerManager.configureWidget(project)
        }
        CompileServerLauncher.ensureServerRunning(project)
      }
    }
  }

  ScalaShutDownTracker.registerShutdownTask(() => {
    LOG.info("Shutdown event triggered, stopping server")
    ensureServerNotRunning()
  })

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
      LOG.traceWithDebugInDev("waiting for compile server initialization...")
      sendDummyRequest(project)
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
        invokeLater {
          CompileServerManager.configureWidget(project)
        }
        CompileServerNotificationsService.get(project).resetNotifications()
        true
      case Left(error)  =>
        val title = ScalaBundle.message("cannot.start.scala.compile.server")
        val groupId = "scala"
        error match {
          case CompileServerProblem.SdkNotSpecified =>
            val text =
              s"""No SDK specified.<p/>
                 |Please specify it in the <a href="">Compile Server Settings</a>
                 |""".stripMargin
            val listener: NotificationListener = { (notification: Notification, _: HyperlinkEvent) =>
              notification.expire()
              CompileServerManager.showCompileServerSettingsDialog(project)
            }
            Notifications.Bus.notify(new Notification(groupId, title, text, NotificationType.ERROR, listener))
          case error: CompileServerProblem.Error =>
            val text = ScalaBundle.nls("jdk.for.compiler.process.not.found", error.text)
            Notifications.Bus.notify(new Notification(groupId, title, text, NotificationType.ERROR))
            LOG.error(title, text)
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

  private def start(project: Project, jdk: JDK): Either[CompileServerProblem.Error, Process] = {
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
          // in worksheet tests we reuse compile server between project
          // so we initialize it before the first test starts
          val pluginsClasspath = if (isUnitTestMode && project == null) Seq() else
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
        val buildSystemDir = BuildManager.getInstance.getBuildSystemDirectory(project)
        deleteOldTokenFile(buildSystemDir, freePort)
        val id = settings.COMPILE_SERVER_ID

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay=$shutdownDelay")
        } else Nil
        val isScalaCompileServer = s"-D${CompileServerProperties.IsScalaCompileServer}=true"
        val parallelCompilation = s"-D${GlobalOptions.COMPILE_PARALLEL_OPTION}=${settings.COMPILE_SERVER_PARALLEL_COMPILATION}"

        val vmOptions: Seq[String] = if (isUnitTestMode && project == null) Seq() else {
          val buildProcessParameters = BuildProcessParametersProvider.EP_NAME.getExtensions(project).asScala.iterator
            .flatMap(_.getVMArguments.asScala)
          val extraJvmParameters = CompileServerVmOptionsProvider.implementations.iterator
            .flatMap(_.vmOptionsFor(project))
          (buildProcessParameters ++ extraJvmParameters).to(ArraySeq)
        }

        // SCL-18193
        val addOpensOptions = if (jdk.version.exists(_ isAtLeast JavaSdkVersion.JDK_1_9))
          JvmOptions.addOpens(
            "java.base/java.util",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.compiler/com.sun.tools.javac.util",
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
            parallelCompilation +:
            addOpensOptions ++:
            vmOptions ++:
            NailgunRunnerFQN +:
            freePort.toString +:
            id +:
            classpath.mkString(File.pathSeparator) +:
            buildSystemDir.toFile.getCanonicalPath +:
            Nil

        val builder = new ProcessBuilder(commands.asJava)

        if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
          projectHome(project).foreach(dir => builder.directory(dir))
        }

        catching(classOf[IOException])
          .either(builder.start())
          .left.map(e => CompileServerProblem.Error(NlsString.force(e.getMessage)))
          .map { process =>
            val watcher = new ProcessWatcher(process, "scalaCompileServer")
            val instance = ServerInstance(watcher, freePort, builder.directory(), jdk, userJvmParameters.toSet)
            serverInstance = Some(instance)
            watcher.startNotify()
            infoAndPrintOnTeamcity(s"compile server process started : ${instance.summary}")
            process
          }
      case (_, absentFiles) =>
        val paths = absentFiles.map(_.getPath).mkString(", ")
        Left(CompileServerProblem.Error(ScalaBundle.message("required.file.not.found.paths", paths)))
    }
  }

  // ensure that old tokens from old sessions do not exist on file system to avoid race conditions (see ticket from the commit)
  // it should be deleted in org.jetbrains.plugins.scala.nailgun.NailgunRunner.ShutdownHook.run
  // but in case of some server crashes it can remain on the file system
  private def deleteOldTokenFile(buildSystemDir: Path, freePort: Int): Unit =
    Try(Files.delete(CompileServerToken.tokenPathForPort(buildSystemDir, freePort)))

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

  def stopForProject(project: Project, debugReason: Option[String] = None): Unit = {
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
      .getOrElse(CompileServerJdkManager.getBuildProcessRuntimeSdk(project))

  def compileServerSdk(project: Project): Either[CompileServerProblem, Sdk] = {
    val settings = ScalaCompileServerSettings.getInstance()

    val sdk =
      if (settings.USE_DEFAULT_SDK)
        Option(defaultSdk(project))
          .toRight(CompileServerProblem.Error(ScalaBundle.message("can.t.find.default.jdk")))
      else if (settings.COMPILE_SERVER_SDK != null)
        Option(ProjectJdkTable.getInstance().findJdk(settings.COMPILE_SERVER_SDK))
          .toRight(CompileServerProblem.Error(ScalaBundle.message("cant.find.jdk", settings.COMPILE_SERVER_SDK)))
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
    IntellijPlatformJars.trove4jJar,
    IntellijPlatformJars.protobufJava,
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
  )

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
        Some(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=5006")
      } else None

    xmx ++ otherParams ++ debugAgent
  }

  private val serverStartLock = new Object

  // TODO: make it thread safe, call from a single thread OR use some locking mechanism

  def ensureServerRunning(project: Project): Boolean = serverStartLock.synchronized {
    LOG.traceWithDebugInDev(s"ensureServerRunning [thread:${Thread.currentThread.getId}]")
    val reasons = restartReasons(project)
    if (reasons.nonEmpty)
      stop(timeoutMs = 3000L, debugReason = Some(s"needsRestart: ${reasons.mkString(", ")}"))

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
      if (jvmParametersChanged) reasons += "jvm parameters"
      reasons.toSeq
    }.getOrElse(Seq.empty)
  }

  def ensureServerNotRunning(project: Project): Unit = serverStartLock.synchronized {
    if (running) stopForProject(project, debugReason = Some("ensureServerNotRunning (for project)"))
  }

  private def ensureServerNotRunning(): Unit = serverStartLock.synchronized {
    if (running) stop(debugReason = Some("ensureServerNotRunning"))
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

  sealed trait CompileServerProblem

  object CompileServerProblem {
    final case object SdkNotSpecified extends CompileServerProblem
    final case class Error(@Nls text: String) extends CompileServerProblem
  }
}

private case class ServerInstance(watcher: ProcessWatcher,
                                  port: Int,
                                  workingDir: File,
                                  jdk: JDK,
                                  jvmParameters: Set[String]) {
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
      s", jvmParameters: ${jvmParameters.mkString(",")}" +
      s", stopped: $stopped" +
      s", running: $running" +
      s", errors: ${errors().mkString("(", ", ", ")")}"
  }
}
