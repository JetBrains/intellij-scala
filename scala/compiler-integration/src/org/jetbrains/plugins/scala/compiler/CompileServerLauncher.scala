package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.compiler.server.{BuildManagerListener, BuildProcessParametersProvider}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.{ApplicationManager, PathManagerEx}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.{ApiStatus, Nls}
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.server.{CompileServerProperties, CompileServerToken}
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}
import org.jetbrains.plugins.scala.util._

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._
import scala.io.Source

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
    stopServerAndWaitFor(Duration.Zero)
  })

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
          // Duplicated --add-opens parameters are inherited from this extension point
          // through ScalaBuildProcessParametersProvider. This filtering also helps to not
          // pass --add-opens parameters to JDK 8 and lower.
          val buildProcessParameters = BuildProcessParametersProvider.EP_NAME.getExtensions(project).asScala.iterator
            .flatMap(_.getVMArguments.asScala).toSeq.diff(compileServerJvmAddOpensExtraParams)
          val extraJvmParameters = CompileServerVmOptionsProvider.implementations.iterator
            .flatMap(_.vmOptionsFor(project)).toSeq
          buildProcessParameters ++ extraJvmParameters
        }

        // SCL-18193
        val addOpensOptions =
          if (jdk.version.exists(_ isAtLeast JavaSdkVersion.JDK_1_9)) {
            val buffer = mutable.ListBuffer.empty[String]
            ClasspathBootstrap.configureReflectionOpenPackages(buffer.append)
            buffer.result() ++ compileServerJvmAddOpensExtraParams
          } else Seq.empty

        val userJvmParameters = jvmParameters
        val java9rtJarParams = prepareJava9rtJar(jdk)
        val commands =
          jdk.executable.canonicalPath +:
            "-cp" +: nailgunClasspath +:
            userJvmParameters ++:
            java9rtJarParams ++:
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

        val builder = new GeneralCommandLine(commands.asJava)
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .toProcessBuilder

        val customWorkingDir = settings.CUSTOM_WORKING_DIR_FOR_TESTS
        if (customWorkingDir != null) {
          builder.directory(new File(customWorkingDir))
        }
        else if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
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
            val instance = new ServerInstance(project, watcher, freePort, builder.directory(), jdk, userJvmParameters.toSet)
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

  /**
   * Signals the Scala Compile Server to stop.
   *
   * @note This method is blocking. It should not be called on the UI thread.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2024.1")
  @Deprecated(since = "2023.3", forRemoval = true)
  @deprecated(message = "Use stopServerAndWait or stopServerAndWaitFor", since = "2023.3")
  def stop(timeoutMs: Long = 0, debugReason: Option[String] = None): Boolean =
    stopInternal(Some(timeoutMs.millis), debugReason)

  /**
   * Stops the Scala Compile Server and waits for the process to exit.
   */
  def stopServerAndWait(debugReason: Option[String] = None): Boolean =
    stopInternal(None, debugReason)

  /**
   * Stops the Scala Compile Server and waits until the process exits or until the provided timeout expires, whichever
   * comes first.
   */
  def stopServerAndWaitFor(timeout: FiniteDuration, debugReason: Option[String] = None): Boolean =
    stopInternal(Some(timeout), debugReason)

  private def stopInternal(timeout: Option[FiniteDuration], debugReason: Option[String]): Boolean = serverStartLock.synchronized {
    LOG.info(s"compile server process stop: ${serverInstance.map(_.summary).getOrElse("<no info>")}")
    serverInstance.forall { it =>
      val stopped = timeout match {
        case Some(duration) => it.destroyAndWaitFor(duration.toMillis)
        case None => it.destroyAndWait()
      }
      infoAndPrintOnTeamcity(s"compile server process stopped${debugReason.fold("")(", reason: " + _)}")

      if (!stopped) {
        val message = timeout match {
          case Some(duration) =>
            s"Compile server process failed to stop after ${duration.toMillis} ms"
          case None =>
            s"Compile server process failed to stop"
        }

        if (isUnitTestMode) {
          // Log an error and throw assertion error in tests.
          LOG.error(message)
          throw new AssertionError(message)
        } else {
          // Log a warning in production code.
          LOG.warn(message)
        }
      }

      stopped
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
    CompileServerJdkManager.recommendedJdk(project)._1

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

  /**
   * NOTE: extra classpath for JPS process is defined in a differ place in `compiler-integration.xml` in `compileServer.plugin` extension
   */
  def compileServerJars: Seq[File] = Seq(
    IntellijPlatformJars.jpsBuildersJar,
    IntellijPlatformJars.utilJar,
    IntellijPlatformJars.utilRtJar,
    IntellijPlatformJars.protobufJava, // required for org.jetbrains.jps.incremental.scala.remote.Main.compileJpsLogic
    IntellijPlatformJars.fastUtilJar,
    LibraryJars.scalaParserCombinators,
    ScalaPluginJars.scalaLibraryJar,
    ScalaPluginJars.scalaReflectJar,
    ScalaPluginJars.scalaNailgunRunnerJar,
    ScalaPluginJars.compilerSharedJar,
    ScalaPluginJars.scalaJpsJar,
    ScalaPluginJars.nailgunJar,
    ScalaPluginJars.compilerInterfaceJar,
    ScalaPluginJars.sbtInterfaceJar,
    ScalaPluginJars.incrementalCompilerJar,
    ScalaPluginJars.compileServerJar,
    ScalaPluginJars.compilerJpsJar,
    ScalaPluginJars.replInterface,
    ScalaPluginJars.utilsRt,
  ).distinct

  def jvmParameters: Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance()
    val size = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE
    val xmx = if (size.isEmpty) Nil else List(s"-Xmx${size}m")

    val paramsParsed = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").filter(StringUtils.isNotBlank)
    val (_, otherParams) = paramsParsed.partition(_.contains("-XX:MaxPermSize"))

    val debugAgent: Option[String] =
      if (attachDebugAgent) {
        val suspend = if (waitUntilDebuggerAttached) "y" else "n"
        Some(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=$debugAgentPort")
      } else None

    xmx ++ otherParams ++ debugAgent
  }

  /**
   * A cache to avoid recomputing the `rt.jar` location on every invocation of the `prepareJava9rtJar` method,
   * which happens every time the compile server needs to be started for some reason, as well as every time
   * the JPS build process restarts, which is after every build.
   *
   * Only the filesystem path to the argument JDK is kept in the map, not a reference to the underlying SDK object,
   * to avoid memory leaks.
   */
  private val jdkRtJarCache: ConcurrentHashMap[String, Path] = new ConcurrentHashMap()

  /**
   * Prepares the Java 9+ `rt.jar` workaround for compiling old versions of Scala with modern JDK versions.
   *
   * @note This method does heavy I/O which can block for several seconds. It must not be called on the UI thread.
   */
  private[scala] def prepareJava9rtJar(jdk: JDK): Seq[String] = {
    /*
     * The following code is the same workaround that sbt applies that allows unpatched versions of Scala
     * (before Scala 2.10.7, before Scala 2.11.12, before Scala 2.12.17) to be compilable on JDK 9+.
     *
     * This workaround is necessary because old versions of Scala that were published before JDK 9 became public
     * expected the existence of `rt.jar`, a jar containing the runtime classes of the Java Virtual Machine
     * (this includes java.lang.Object, java.lang.String, etc...).
     *
     * With Java 9, the JDK was modularized, and `rt.jar` does not exist anymore. Instead, the JDK is split into
     * modules, which contain the packages and classes of the runtime. So, classes like java.lang.Object and
     * java.lang.String became part of the java.base module, and they can be referred to as
     * java.base/java.lang.Object and java.base/java.lang.String (slightly simplified). In any case, old versions
     * of the Scala compiler do not expect this change, and cannot handle it. By providing the `rt.jar`, we are
     * providing a compatible environment for those old versions of the compiler.
     *
     * https://github.com/sbt/zinc/issues/641#issuecomment-588589420
     *
     * If JDK 8 or lower is used as the runtime for the Scala compiler, no workaround is needed, this is legacy mode.
     *
     * When JDK 9+ is used as the runtime for the Scala compiler, the JVM parameter `-Dscala.ext.dirs` is populated with
     * the artificially produced `rt.jar`, extracted from the runtime JDK.
     *
     * The sbt `java9-rt-export` tool is used to produce the `rt.jar` file, and is unique to each JDK runtime.
     */
    Option(jdk).filter(_.version.exists(_.isAtLeast(JavaSdkVersion.JDK_1_9))).fold(Seq.empty[String]) { jdk =>
      // We are running JDK 9+ as the runtime JDK for the Scala compiler.
      val executablePath = jdk.executable.canonicalPath

      val resultPath =
        if (jdkRtJarCache.containsKey(executablePath)) Some(jdkRtJarCache.get(executablePath))
        else {
          // The path of the `java9-rt-export.jar` tool packaged as `<plugin root>/java9-rt-export/java9-rt-export.jar`
          // and distributed with the Scala plugin.
          val java9rtExportJar =
          Path.of(PathUtil.getJarPathForClass(getClass))
            .getParent
            .getParent
            .resolve(java9rtExportString)
            .resolve(s"$java9rtExportString.jar")

          // The command
          // `java -Dsbt.global.base=<IDEA system directory>/scala-compile-server/jvm-rt -jar <plugin root>/java9-rt-export/java9-rt-export.jar --rt-ext-dir`
          // is executed to obtain a directory for exporting the rt.jar. The directory (and jar) is unique for each JDK
          // runtime, but needs to be exported only once and can be reused on subsequent invocations using the same JDK.
          // The output of the command is a path like the following:
          // <IDEA system directory>/scala-compile-server/jvm-rt/java9-rt-ext-eclipse_adoptium_17_0_5
          // for Eclipse Adoptium 17.0.5
          Try {
            val exportDirectoryPathProcess =
              new GeneralCommandLine(executablePath, s"-Dsbt.global.base=$jvmRtDir", "-jar", java9rtExportJar.toString, "--rt-ext-dir")
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .createProcess()

            exportDirectoryPathProcess.waitFor()
            val exportDirectoryPath =
              Path.of(Source.fromInputStream(exportDirectoryPathProcess.getInputStream).mkString.trim)

            // The full path of the produced `rt.jar`.
            // Example: <IDEA system directory>/scala-compile-server/jvm-rt/java9-rt-ext-eclipse_adoptium_17_0_5/rt.jar
            val rtJarPath = exportDirectoryPath.resolve("rt.jar")

            // Create the export directory if it doesn't exist.
            val exportDirectory = exportDirectoryPath.toFile
            if (!exportDirectory.exists()) {
              exportDirectory.mkdirs()
            }

            // Create the `rt.jar` if it doesn't exist.
            if (!rtJarPath.toFile.exists()) {
              // The command
              // `java -jar <plugin root>/java9-rt-export/java9-rt-export.jar <IDEA system directory>/scala-compile-server/jvm-rt/<jdk specific directory>`
              // is executed and creates the `rt.jar`.
              new GeneralCommandLine(executablePath, "-jar", java9rtExportJar.toString, rtJarPath.toString)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .createProcess()
                .waitFor()
            }

            jdkRtJarCache.put(executablePath, exportDirectoryPath)
            exportDirectoryPath
          }.toOption
        }

      // The path of the directory with the exported `rt.jar` is provided as a JVM parameter
      // `-Dscala.ext.dirs=<IDEA system directory>/scala-compile-server/jvm-rt/<jdk specific directory>`
      resultPath.map(path => s"$scalaExtDirsParameterString=$path").toSeq
    }
  }

  private val serverStartLock = new Object

  // TODO: make it thread safe, call from a single thread OR use some locking mechanism

  /**
   * Starts the Scala Compile Server.
   *
   * @note This method is blocking. It should not be called on the UI thread.
   */
  def ensureServerRunning(project: Project): Boolean = serverStartLock.synchronized {
    LOG.traceWithDebugInDev(s"ensureServerRunning [thread:${Thread.currentThread.getId}]")
    if (project.isDisposed) {
      LOG.warn(s"ensureServerRunning is invoked for a disposed project: $project")
      return false
    }
    val reasons = restartReasons(project)
    if (reasons.nonEmpty) {
      val stopped = stopServerAndWait(debugReason = Some(s"needs to restart: ${reasons.mkString(", ")}"))
      if (!stopped && isUnitTestMode) {
        LOG.error("couldn't stop compile server")
      }
    }

    running || start(project)
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
      if (!isUnitTestMode && instance.project.isDisposed) {
        // We intentionally reuse the compile server in worksheet tests. This check would
        // otherwise stop and start the compile server before each test, since each test
        // spawns a new instance (JVM object instance) of the same project on disk.
        reasons += "running instance project disposed"
      }
      if (workingDirChanged) reasons += "working dir changed"
      if (jdkChanged) reasons += "jdk changed"
      if (jvmParametersChanged) reasons += "jvm parameters changed"
      reasons.toSeq
    }.getOrElse(Seq.empty)
  }

  /**
   * Stops the Scala Compile Server, but doesn't wait for the process to exit.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2024.1")
  @Deprecated(since = "2023.3", forRemoval = true)
  @deprecated(message = "Use stopServerAndWait or stopServerAndWaitFor", since = "2023.3")
  def ensureServerNotRunning(): Unit = serverStartLock.synchronized {
    if (running) stopInternal(Some(Duration.Zero), debugReason = Some("ensureServerNotRunning"))
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

  private def saveSettings(): Unit = {
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

  private def jvmRtDir: Path = scalaCompileServerSystemDir.resolve("jvm-rt")

  private val java9rtExportString: String = "java9-rt-export"

  private val scalaExtDirsParameterString: String = "-Dscala.ext.dirs"

  private[compiler] val compileServerJvmAddOpensExtraParams: Seq[String] =
    Seq(
      "java.base/java.nio",
      "java.base/java.util",
      "java.base/sun.nio.ch"
    ).flatMap { modulePackage =>
      Seq("--add-opens", s"$modulePackage=ALL-UNNAMED")
    }
}
