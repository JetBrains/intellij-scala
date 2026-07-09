//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.compiler.server.{BuildManager, BuildProcessParametersProvider}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager, PathManager}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.{Project, ProjectManager, ProjectUtil}
import com.intellij.openapi.projectRoots.{JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.{EelPathUtils, EelProjectUtils, EelSystemFolderUtils}
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.eel.{EelDescriptor, EelPlatformKt}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.{Nls, Nullable, TestOnly}
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.plugins.scala.compiler.EelCompilerUtils.asTargetLocalPathString
import org.jetbrains.plugins.scala.compiler.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.eel.tunnels.EelTunnels
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.server.{CompileServerPort, CompileServerProperties, CompileServerToken}
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils

import java.io.{BufferedReader, IOException, InputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, CopyOnWriteArrayList}
import kotlinx.coroutines.CoroutineScope
import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.control.Exception._
import scala.util.matching.Regex
import scala.util.{Try, Using}

object CompileServerLauncher {

  @volatile private var serverInstance: Option[ServerInstance] = None
  private val LOG = Logger.getInstance(getClass)

  private val NailgunRunnerFQN = "org.jetbrains.plugins.scala.nailgun.NailgunRunner"

  private def attachDebugAgent = false
  private def waitUntilDebuggerAttached = true
  private def debugAgentPort = "5006"

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

  // TODO: track that we attach debug agent and show notification, as with JPS Build Process
  // TODO: add internal action "Debug Scala Compile Server" as with JPS "Debug Build Process"
  private def start(project: Project, jdk: JDK): Either[CompileServerProblem, Process] = {
    LOG.traceWithDebugInDev(s"starting server")

    val settings = ScalaCompileServerSettings.getInstance

    if (settings.COMPILE_SERVER_SDK != jdk.name) {
      settings.COMPILE_SERVER_SDK = jdk.name
      ApplicationManager.getApplication.saveSettings()
    }

    val eelDescriptor = EelProviderUtil.getEelDescriptor(project)

    compileServerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
        val (nailgunCpFiles, classpathFiles) = presentFiles.partition(_.nameContains("nailgun"))

        val targetPathSeparator = EelPlatformKt.getPathSeparator(eelDescriptor.getOsFamily)

        val nailgunClasspath =
          nailgunCpFiles
            .map(transferredRemotePath(_, project, eelDescriptor))
            .map(asTargetLocalPathString(_, eelDescriptor))
            .mkString(targetPathSeparator)

        val buildProcessClasspath =
          if (project.isDisposed)
            Seq.empty
          else
            new BuildProcessClasspathManager(project.unloadAwareDisposable)
              .getBuildProcessClasspath(project)
              .asScala
              .toSeq
              .map(Path.of(_))

        val classpath =
          (jdk.tools.toSeq ++ classpathFiles ++ buildProcessClasspath)
            .map(transferredRemotePath(_, project, eelDescriptor))
            .map(asTargetLocalPathString(_, eelDescriptor))
            .mkString(targetPathSeparator)

        // Remote eel-specific preparation: transfer compiler bridge sources and worksheet repl interface impls jar to the remote machine.
        if (eelDescriptor != LocalEelDescriptor.INSTANCE) {
          CompilerBridgeSourcesJars.allBridgeSources.foreach { path =>
            transferredRemotePath(path, project, eelDescriptor)
          }
          transferWorksheetReplInterfaceImpls(project, eelDescriptor)
        }

        val id = settings.COMPILE_SERVER_ID

        val compileServerSystemDir = scalaCompileServerSystemDir(project)

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY * 60
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay.seconds=$shutdownDelay")
        } else Nil
        val isScalaCompileServer = s"-D${CompileServerProperties.IsScalaCompileServer}=true"

        val jpsUseUnifiedIC = isJpsUseUnifiedIC

        val vmOptions = if (project.isDisposed) Seq.empty else {
          // Duplicated --add-opens parameters are inherited from this extension point
          // through ScalaBuildProcessParametersProvider. This filtering also helps to not
          // pass --add-opens parameters to JDK 8 and lower.
          val buildProcessParameters = BuildProcessParametersProvider.EP_NAME.getExtensions(project).asScala.iterator
            .flatMap(_.getVMArguments.asScala).toSeq.diff(compileServerJvmAddOpensExtraParams)

          val jpsOptions =
            if (jpsUseUnifiedIC) Seq(s"-D${GlobalOptions.DEPENDENCY_GRAPH_ENABLED}=true")
            else Seq.empty

          buildProcessParameters ++ jpsOptions
        }

        // SCL-18193
        val addOpensOptions =
          if (isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_9)) {
            val buffer = mutable.ListBuffer.empty[String]
            ClasspathBootstrap.configureReflectionOpenPackages(buffer.append)
            buffer.result() ++ compileServerJvmAddOpensExtraParams
          } else Seq.empty

        // SCL-23766 Unsafe memory access in JDK 24+
        val unsafeMemoryAccessOptions =
          if (isJdkAtLeast(jdk, JavaSdkVersion.JDK_24))
            Seq("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
          else Seq.empty

        // SCL-25201 Final field mutation warning in JDK 26+
        val finalFieldMutationOptions =
          if (isJdkAtLeast(jdk, JavaSdkVersion.JDK_26))
            Seq("--enable-final-field-mutation=ALL-UNNAMED")
          else Seq.empty

        val userJvmParameters = jvmParameters
        val java9rtJarParams = prepareJava9rtJar(project, jdk)

        val loggingParameters = {
          val vmOptionsForLogging =
            if (isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_9))
              Seq(s"--add-opens", "java.base/java.lang=ALL-UNNAMED")
            else
              Seq.empty

          Seq(
            s"-D${GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION}=false",
            s"-D${CompileServerProperties.LogDirectory}=${asTargetLocalPathString(logDirectory(eelDescriptor), eelDescriptor)}"
          ) ++ vmOptionsForLogging
        }

        val jpsBuildSystemDir = BuildManager.getInstance().getBuildSystemDirectory(project)

        val commands =
          jdk.executable.toCanonicalPath.toString +:
            "-cp" +: nailgunClasspath +:
            userJvmParameters ++:
            jnaVMOptions ++:
            java9rtJarParams ++:
            shutdownDelayArg ++:
            isScalaCompileServer +:
            addOpensOptions ++:
            unsafeMemoryAccessOptions ++:
            finalFieldMutationOptions ++:
            vmOptions ++:
            loggingParameters ++:
            NailgunRunnerFQN +:
            id +:
            classpath +:
            asTargetLocalPathString(compileServerSystemDir, eelDescriptor) +:
            asTargetLocalPathString(jpsBuildSystemDir, eelDescriptor) +:
            Nil

        val workingDirectory: Path = {
          val customWorkingDir = settings.CUSTOM_WORKING_DIR_FOR_TESTS
          if (customWorkingDir ne null) Path.of(customWorkingDir).toCanonicalPath
          else if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) projectHome(project).map(_.toCanonicalPath).orNull
          else null
        }

        val builder = new GeneralCommandLine(commands.asJava)
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withWorkingDirectory(workingDirectory)
        val commandLineString = builder.getCommandLineString

        val incrementalCompiler = ScalaCompilerConfiguration(project).incrementalityType

        catching(classOf[IOException])
          .either(builder.createProcess())
          .left.map(e => CompileServerProblem.UnexpectedException(e))
          .map { process =>
            val local = EelProjectUtils.isProjectLocal(project)
            val port = {
              val portReportedByServer = waitUntilNailgunServerIsReady(compileServerSystemDir, process.getInputStream) match {
                case Some(p) => p
                case None =>
                  return Left(CompileServerProblem.Error(CompilerIntegrationBundle.message("compile.server.missing.tcp.port")))
              }

              if (local) CompileServerPort.Local(portReportedByServer)
              else {
                val eelApi = EelProviderUtil.toEelApiBlocking(eelDescriptor)
                val tunnels = eelApi.getTunnels
                val scope = CoroutineScopeProvider.scope(project)
                val forwardedLocalPort = EelTunnels.forwardLocalPort(scope, tunnels, portReportedByServer)
                CompileServerPort.Remote(forwardedLocalPort, portReportedByServer)
              }
            }

            writePortFile(compileServerSystemDir, port.forToken)

            val watcher = new ProcessWatcher(process, "scalaCompileServer", local)
            // ATTENTION: this stack trace captures only the synchronous process-creation path.
            // If the launch is scheduled through platform background executors, the trace may start in a pooled thread and may not
            // contain the original test method.
            // Our investigation did not find a reliable JUnit 3 current-test context to query here.
            // If these tests move to JUnit 5, consider enriching this diagnostic with the
            // IDE Starter holder `com.intellij.ide.starter.runner.CurrentTestMethod`, populated by
            // `com.intellij.ide.starter.junit5.CurrentTestMethodProvider`; that should at least show which test
            // was active when the compiler server was started.
            val processStartStackTrace = new Throwable(
              s"Scala Compile Server process start stack trace [thread=${Thread.currentThread.getName}]"
            )
            val instance = new ServerInstance(
              watcher = watcher,
              createdAtStackTrace = processStartStackTrace,
              compileServerSystemDir = compileServerSystemDir,
              port = port,
              workingDir = Option(workingDirectory),
              jdk = jdk,
              jvmParameters = userJvmParameters.toSet,
              jpsUseUnifiedIC = jpsUseUnifiedIC,
              incrementalCompiler = incrementalCompiler
            )
            LOG.assertTrue(serverInstance.isEmpty, "serverInstance is expected to be None")
            serverInstance = Some(instance)
            updateCompileServerWidget()
            watcher.startNotify()
            watcher.addProcessListener(new ProcessListener {
              override def processTerminated(event: ProcessEvent): Unit = {
                CompileServerToken.removeTokenFileForPort(compileServerSystemDir, port.forToken)
                val isExpectedProcessTermination = watcher.isTerminatedByIdleTimeout || instance.stopped
                if (!isExpectedProcessTermination) {
                  LOG.warn(s"Compile server terminated unexpectedly: ${instance.summary}")
                  invokeLater {
                    ProjectManager.getInstance().getOpenProjects.foreach { project =>
                      if (!project.isDisposed) {
                        CompileServerNotifications.showNotification(
                          CompilerIntegrationBundle.message("compile.server.terminated.unexpectedly.0.port.1.pid", instance.port, instance.pid),
                          NotificationType.WARNING,
                          Some(project)
                        )
                      }
                    }
                  }
                }

                serverInstance = None
                updateCompileServerWidget()
              }
            })
            infoAndPrintOnTeamcity(s"compile server process started: ${instance.summary}")
            LOG.debug(s"command line: $commandLineString")
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
        val paths = absentFiles.mkString(", ")
        Left(CompileServerProblem.Error(CompilerIntegrationBundle.message("required.file.not.found.paths", paths)))
    }
  }

  private def updateCompileServerWidget(): Unit = {
    val app = ApplicationManager.getApplication
    if (app.isDisposed) return
    app.getMessageBus.syncPublisher(CompileServerWidgetFactory.Topic).updateWidget()
  }

  // TODO stop server more gracefully

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
    val stopped = serverInstance.forall { it =>
      val stopped = timeout match {
        case Some(duration) => it.destroyAndWaitFor(duration.toMillis)
        case None => it.destroyAndWait()
      }
      infoAndPrintOnTeamcity(s"compile server process stopped${debugReason.fold("")(", reason: " + _)}")

      // Do not log anything if not waiting for the compile server to exit at all
      if (!stopped && !timeout.contains(Duration.Zero)) {
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

      if (stopped) {
        CompileServerToken.removeTokenFileForPort(it.compileServerSystemDir, it.port.forToken)
      }

      stopped
    }
    if (stopped) {
      serverInstance = None
    }
    stopped
  }

  private def infoAndPrintOnTeamcity(message: String): Unit = {
    LOG.info(message)
    TeamcityUtils.logUnderTeamcity(message)
  }

  def running: Boolean = serverInstance.exists(_.running)

  @TestOnly
  final case class RunningServerStateForTests(
    wasRunning: Boolean,
    startStackTrace: Option[Throwable]
  )

  @TestOnly
  final case class ServerStartRequestForTests(
    projectName: String,
    stackTrace: Throwable
  )

  // Test watchers observe requests to enter the compile-server path, not only actual process starts.
  // If a server is already running, ensureServerRunning may return without creating a process, but the
  // request is still useful evidence for tests which assert that sbt-shell builds avoid JPS compilation.
  @TestOnly
  final class ServerStartRequestsWatcherForTests private[CompileServerLauncher] {
    private val recordedRequests = new ConcurrentLinkedQueue[ServerStartRequestForTests]

    private[CompileServerLauncher] def record(request: ServerStartRequestForTests): Unit =
      recordedRequests.add(request)

    def requests: Seq[ServerStartRequestForTests] =
      recordedRequests.asScala.toSeq
  }

  private val serverStartRequestWatchersForTests =
    new CopyOnWriteArrayList[ServerStartRequestsWatcherForTests]

  @TestOnly
  def captureRunningServerStateForTests: RunningServerStateForTests =
    serverStartLock.synchronized {
      val runningInstance = serverInstance.filter(_.running)
      RunningServerStateForTests(
        wasRunning = runningInstance.nonEmpty,
        startStackTrace = runningInstance.map(_.createdAtStackTrace)
      )
    }

  @TestOnly
  def watchServerStartRequestsForTests(parentDisposable: Disposable): ServerStartRequestsWatcherForTests = {
    val watcher = new ServerStartRequestsWatcherForTests
    serverStartRequestWatchersForTests.add(watcher)
    Disposer.register(parentDisposable, () => {
      serverStartRequestWatchersForTests.remove(watcher)
      ()
    })
    watcher
  }

  def compileServerPort: Option[CompileServerPort] = serverInstance.map(_.port)

  def pid: Option[Long] = serverInstance.flatMap(_.watcher.pid)

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
    sdk.flatMap(JDK.fromSdk)
  }

  def compileServerJdkFeatureVersion(project: Project): Option[Int] =
    for {
      jdk <- compileServerJdk(project).toOption
      version <- jdk.version
    } yield version.getMaxLanguageLevel.feature()

  /**
   * NOTE: extra classpath for JPS process is defined in a differ place in `compiler-integration.xml` in `compileServer.plugin` extension
   */
  def compileServerJars: Seq[Path] = Seq(
    IntellijPlatformJars.jpsBuildersJar,
    IntellijPlatformJars.utilJar,
    IntellijPlatformJars.utilRtJar,
    IntellijPlatformJars.protobufJava, // required for org.jetbrains.jps.incremental.scala.remote.Main.compileJpsLogic
    IntellijPlatformJars.fastUtilJar,
    IntellijPlatformJars.asmJar,
    LibraryJars.scalaParserCombinators,
    ScalaPluginJars.scalaLibraryJar,
    ScalaPluginJars.scala3LibraryJar,
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
    val xmx = size.toIntOption.map(sz => s"-Xmx${sz}m").toList

    val paramsParsed = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").filter(StringUtils.isNotBlank)
    val (_, otherParams) = paramsParsed.partition(_.contains("-XX:MaxPermSize"))

    val debugAgent: Option[String] =
      if (attachDebugAgent) {
        val suspend = if (waitUntilDebuggerAttached) "y" else "n"
        Some(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=$debugAgentPort")
      } else None

    xmx ++ otherParams ++ debugAgent
  }

  private def isJpsUseUnifiedIC: Boolean = AdvancedSettings.getBoolean("compiler.unified.ic.implementation")

  /**
   * Same parameters as the ones provided by the IntelliJ platform to the JPS build process.
   *
   * The JNA VM options are added directly to the Java command line, instead of being added to the
   * BuildProcessParameterersProvider extension point, because the platform adds them directly to the JPS process Java
   * command line, and it would lead to duplication.
   *
   * @see [[com.intellij.compiler.server.BuildManager#launchBuildProcess]].
   */
  private[scala] def jnaVMOptions: Seq[String] =
    sys.props.get("jna.boot.library.path").map { path =>
      Seq(
        s"-Djna.boot.library.path=$path",
        "-Djna.nosys=true",
        "-Djna.noclasspath=true"
      )
    }.getOrElse(Seq.empty)

  private def isJdkAtLeast(jdk: JDK, version: JavaSdkVersion): Boolean =
    jdk.version.exists(_.isAtLeast(version))

  /**
   * A cache to avoid recomputing the `rt.jar` location on every invocation of the `prepareJava9rtJar` method,
   * which happens every time the compile server needs to be started for some reason, as well as every time
   * the JPS build process restarts, which is after every build.
   *
   * Only the filesystem path to the argument JDK is kept in the map, not a reference to the underlying SDK object,
   * to avoid memory leaks.
   */
  private val jdkRtJarCache: ConcurrentHashMap[Path, Path] = new ConcurrentHashMap()

  /**
   * Prepares the Java 9+ `rt.jar` workaround for compiling old versions of Scala with modern JDK versions.
   *
   * @note This method does heavy I/O which can block for several seconds. It must not be called on the UI thread.
   */
  private[scala] def prepareJava9rtJar(project: Project, jdk: JDK): Seq[String] = {
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
    Option(jdk).filter(isJdkAtLeast(_, JavaSdkVersion.JDK_1_9)).fold(Seq.empty[String]) { jdk =>
      // We are running JDK 9+ as the runtime JDK for the Scala compiler.
      val executablePath = jdk.executable.toCanonicalPath
      val eelDescriptor = EelProviderUtil.getEelDescriptor(project)

      val resultPath =
        if (jdkRtJarCache.containsKey(executablePath)) Some(jdkRtJarCache.get(executablePath))
        else {
          // The path of the `java9-rt-export.jar` tool packaged as `<plugin root>/java9-rt-export/java9-rt-export.jar`
          // and distributed with the Scala plugin.
          val java9rtExportJar =
            PathManager.getJarForClass(getClass)
              .getParent
              .getParent
              .getParent
              .resolve(java9rtExportString)
              .resolve(s"$java9rtExportString.jar")

          val transferredJava9rtExportJarPath = asTargetLocalPathString(
            transferredRemotePath(java9rtExportJar, project, eelDescriptor),
            eelDescriptor
          )

          // The command
          // `java -Dsbt.global.base=<IDEA system directory>/scala-compile-server/jvm-rt -jar <plugin root>/java9-rt-export/java9-rt-export.jar --rt-ext-dir`
          // is executed to obtain a directory for exporting the rt.jar. The directory (and jar) is unique for each JDK
          // runtime, but needs to be exported only once and can be reused on subsequent invocations using the same JDK.
          // The output of the command is a path like the following:
          // <IDEA system directory>/scala-compile-server/jvm-rt/java9-rt-ext-eclipse_adoptium_17_0_5
          // for Eclipse Adoptium 17.0.5
          Try {
            val jvmRtDir = asTargetLocalPathString(
              scalaCompileServerSystemDir(project) / "jvm-rt",
              eelDescriptor
            )

            val exportDirectoryPathProcess =
              new GeneralCommandLine(executablePath.toString, s"-Dsbt.global.base=$jvmRtDir", "-jar", transferredJava9rtExportJarPath, "--rt-ext-dir")
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .createProcess()

            exportDirectoryPathProcess.waitFor()
            val rawPathStringOutput =
              Using.resource(Source.fromInputStream(exportDirectoryPathProcess.getInputStream))(_.mkString.trim)
            val exportDirectoryPath = EelNioBridgeServiceKt.asNioPath(EelPath.parse(rawPathStringOutput, eelDescriptor))

            // The full path of the produced `rt.jar`.
            // Example: <IDEA system directory>/scala-compile-server/jvm-rt/java9-rt-ext-eclipse_adoptium_17_0_5/rt.jar
            val rtJarPath = exportDirectoryPath.resolve("rt.jar")

            // Create the export directory if it doesn't exist.
            if (!exportDirectoryPath.exists) {
              Files.createDirectories(exportDirectoryPath)
            }

            // Create the `rt.jar` if it doesn't exist.
            if (!rtJarPath.exists) {
              val rtJarTargetLocalPathString = asTargetLocalPathString(rtJarPath, eelDescriptor)
              // The command
              // `java -jar <plugin root>/java9-rt-export/java9-rt-export.jar <IDEA system directory>/scala-compile-server/jvm-rt/<jdk specific directory>`
              // is executed and creates the `rt.jar`.
              new GeneralCommandLine(executablePath.toString, "-jar", transferredJava9rtExportJarPath, rtJarTargetLocalPathString)
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
      resultPath
        .map(asTargetLocalPathString(_, eelDescriptor))
        .map(path => s"$scalaExtDirsParameterString=$path")
        .toSeq
    }
  }

  private val serverStartLock = new Object

  // TODO: make it thread safe, call from a single thread OR use some locking mechanism

  /**
   * Starts the Scala Compile Server.
   *
   * @note This method is blocking. It should not be called on the UI thread.
   */
  @RequiresBackgroundThread
  def ensureServerRunning(project: Project): Boolean = {
    CompileServerShutdown.registerShutdownTask()
    serverStartLock.synchronized {
      LOG.traceWithDebugInDev(s"ensureServerRunning [thread:${Thread.currentThread.threadId()}]")
      // Record before any early exit so tests can detect even unsuccessful attempts to reach the launcher.
      recordServerStartRequestForTests(project)
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
  }

  private def recordServerStartRequestForTests(project: Project): Unit = {
    if (serverStartRequestWatchersForTests.isEmpty) return

    val request = ServerStartRequestForTests(
      projectName = project.getName,
      stackTrace = new Throwable(
        s"Scala Compile Server start request stack trace [project=${project.getName}, thread=${Thread.currentThread.getName}]"
      )
    )
    serverStartRequestWatchersForTests.asScala.foreach(_.record(request))
  }

  private def restartReasons(project: Project): Seq[String] = {
    val currentInstance = serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    currentInstance.map { instance =>
      val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
      val workingDirChanged = useProjectHome && projectHome(project) != currentInstance.map(_.workingDir)
      val systemDirectoryChanged = instance.compileServerSystemDir != scalaCompileServerSystemDir(project)
      val jdkChanged = compileServerJdk(project) match {
        case Right(projectJdk) => projectJdk != instance.jdk
        case _ => false
      }
      val jvmParametersChanged = jvmParameters.toSet != instance.jvmParameters
      val jpsUseUnifiedICChanged = isJpsUseUnifiedIC != instance.jpsUseUnifiedIC
      val incrementalCompilerChanged = ScalaCompilerConfiguration(project).incrementalityType != instance.incrementalCompiler
      val reasons = mutable.ArrayBuffer.empty[String]
      if (workingDirChanged) reasons += "working dir changed"
      if (systemDirectoryChanged) reasons += "system directory changed"
      if (jdkChanged) reasons += "jdk changed"
      if (jvmParametersChanged) reasons += "jvm parameters changed"
      if (jpsUseUnifiedICChanged) reasons += "jps unified incremental compilation setting changed"
      if (incrementalCompilerChanged) reasons += "incremental compiler changed"
      reasons.toSeq
    }.getOrElse(Seq.empty)
  }

  private val ngServerStartedLine: Regex =
    s"""^NGServer ${BuildInfo.nailgunVersion} started on (.*), port (\\d{1,5})\\.$$""".r

  /**
   * @note The provided [[InputStream]] should not be closed after use, as that would prevent the process
   *       from printing to the out stream.
   *
   * @param is the input stream of the nailgun process
   * @return the TCP port number on which the server is listening
   */
  @RequiresBackgroundThread
  private def waitUntilNailgunServerIsReady(compileServerSystemDir: Path, is: InputStream): Option[Int] = {
    val bufferedReader = new BufferedReader(new InputStreamReader(is))
    while (true) {
      bufferedReader.readLine() match {
        case null => return None
        case ngServerStartedLine(_, port) =>
          // The NGServer is ready to accept connections.
          val opt = port.toIntOption
          opt.foreach(p => CompileServerToken.generateAndWriteTokenFor(compileServerSystemDir, p))
          return opt
        case _ =>
      }
    }
    None
  }

  private def projectHome(project: Project): Option[Path] = {
    for {
      dir <- Option(project.baseDir)
      path <- Option(dir.getFileSystem.getNioPath(dir)) if path.exists
    } yield path
  }

  sealed trait CompileServerProblem

  object CompileServerProblem {
    final case object SdkNotSpecified extends CompileServerProblem
    final case class Error(@Nls text: String) extends CompileServerProblem
    final case class UnexpectedException(cause: Throwable) extends CompileServerProblem
  }

  private final val ScalaCompileServerDirName = "scala-compile-server"

  def scalaCompileServerSystemDir(project: Project): Path = {
    val eelDescriptor = EelProviderUtil.getEelDescriptor(project)
    scalaCompileServerSystemDir(eelDescriptor)
  }

  def logDirectory(@Nullable project: Project): Path = project match {
    case null => buildManagerLogDirectory()
    case p =>
      val descriptor = EelProviderUtil.getEelDescriptor(p)
      logDirectory(descriptor)
  }

  private def logDirectory(eelDescriptor: EelDescriptor): Path =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        buildManagerLogDirectory()
      case remote =>
        EelSystemFolderUtils.getSystemFolder(remote) / "logs" / "build-log"
    }

  private def buildManagerLogDirectory(): Path =
    BuildManager.getBuildLogDirectory.toCanonicalPath

  private def scalaCompileServerSystemDir(eelDescriptor: EelDescriptor): Path = {
    val systemDir = PathUtil.getSystemDirectory(eelDescriptor)
    systemDir.resolve(ScalaCompileServerDirName)
  }

  def targetLocalScalaCompileServerSystemDir(project: Project): String = {
    val eelDescriptor = EelProviderUtil.getEelDescriptor(project)
    val dir = scalaCompileServerSystemDir(eelDescriptor)
    asTargetLocalPathString(dir, eelDescriptor)
  }

  /**
   * Transfers a local file to the remote project cache directory if the project is remote, or returns the path as-is for local projects.
   *
   * @see [[remoteProjectCacheDirectory]]
   */
  def transferredRemotePath(path: Path, project: Project, eelDescriptor: EelDescriptor): Path =
    remoteProjectCacheDirectory(project, eelDescriptor) match {
      case Some(cacheDir) =>
        EelPathUtils.transferLocalContentToRemote(path, new EelPathUtils.TransferTarget.Explicit(cacheDir / path.getFileName))
      case None =>
        path
    }

  private def transferWorksheetReplInterfaceImpls(project: Project, eelDescriptor: EelDescriptor): Unit = {
    remoteProjectCacheDirectory(project, eelDescriptor).foreach { cacheDir =>
      val impls = ScalaPluginJars.worksheetReplInterfaceImplsJar
      val nameCount = impls.getNameCount
      val targetDir = cacheDir.getParent / impls.subpath(nameCount - 2, nameCount - 1) // worksheet-repl-interface
      if (!targetDir.exists) Files.createDirectories(targetDir)
      EelPathUtils.transferLocalContentToRemote(impls, new EelPathUtils.TransferTarget.Explicit(targetDir / impls.getFileName))
    }
  }

  /**
   * Needs to match `cacheDirectory` in [[com.intellij.compiler.server.EelBuildCommandLineBuilder]].
   * @return a path to the cache directory if the project belongs to a remote machine, `None` if the
   *         project is a project on the local machine where IDEA is running
   */
  private def remoteProjectCacheDirectory(project: Project, eelDescriptor: EelDescriptor): Option[Path] =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE => None
      case remote =>
        val systemDir = EelSystemFolderUtils.getSystemFolder(remote)
        val buildId = ApplicationInfo.getInstance().getBuild.toString
        val projectSpecific = ProjectUtil.getProjectCacheFileName(project)
        val path = systemDir / s"jps-$buildId" / projectSpecific
        if (!path.exists) Files.createDirectories(path)
        Some(path)
    }

  private val java9rtExportString: String = "java9-rt-export"

  private val scalaExtDirsParameterString: String = "-Dscala.ext.dirs"

  private def writePortFile(compileServerSystemDir: Path, port: Int): Unit = {
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    val path = CompileServerPort.portFilePath(compileServerSystemDir)
    Files.writeString(path, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
  }

  private[compiler] val compileServerJvmAddOpensExtraParams: Seq[String] =
    Seq(
      "java.base/java.nio",
      "java.base/java.util",
      "java.base/sun.nio.ch",
      "java.base/jdk.internal.ref"
    ).flatMap { modulePackage =>
      Seq("--add-opens", s"$modulePackage=ALL-UNNAMED")
    }

  @Service(Array(Service.Level.PROJECT))
  private final class CoroutineScopeProvider(private val scope: CoroutineScope)

  private object CoroutineScopeProvider {
    def scope(project: Project): CoroutineScope =
      project.getService(classOf[CoroutineScopeProvider]).scope
  }
}
