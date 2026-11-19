package org.jetbrains.sbt.shell.build.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.project.Project
import com.intellij.util.system.OS
import org.jetbrains.sbt.shell.SbtProcessManager

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future, Promise, blocking}
import scala.jdk.OptionConverters.RichOptional
import scala.util.{Success, Try}

/**
 * Fails a build over the sbt shell that doesn't finish in time, instead of letting it block the test.
 *
 * In tests, `ProjectTaskManager` runs the sbt shell build command on the calling thread and only returns when the
 * command finishes, so the timeout passed to `blockingGet` never applies. If sbt hangs, the test waits until the CI
 * build is killed (SCL-26340).
 *
 * When the build doesn't finish in time, the watchdog prints thread dumps of the test JVM and the sbt shell JVM and
 * destroys the sbt shell process. The running command then fails, the build returns, and the test fails.
 */
private[util] object SbtShellBuildWatchdog:

  private val SbtShellOutputTailLength = 3000
  private val JstackTimeout = 30.seconds
  private val WatchdogFinishTimeout = 2.minutes

  def failOnTimeout[T](
    project: Project,
    shellProcessHandler: OSProcessHandler,
    timeout: FiniteDuration,
    buildDescription: String,
    sbtShellOutput: () => String,
  )(build: => T): T =
    // Completed with `true` by the watchdog when the build times out, or with `false` when the build finishes first
    val timedOut = Promise[Boolean]()
    val watchdog = Future {
      blocking {
        Try(Await.ready(timedOut.future, timeout))
        if timedOut.trySuccess(true) then
          Try(println(threadDumps(s"$buildDescription didn't finish in $timeout", shellProcessHandler)))
            .failed.foreach(exception => println(s"Couldn't print the thread dumps: $exception"))
          SbtProcessManager.forProject(project).destroyProcess()
      }
    }

    val result = try Try(build) finally timedOut.trySuccess(false)

    if timedOut.future.value.contains(Success(true)) then
      Try(Await.ready(watchdog, WatchdogFinishTimeout))
      val message = Seq(
        s"$buildDescription didn't finish in $timeout.",
        "The sbt shell process was destroyed to unblock the test.",
        "Before that, thread dumps of the test JVM and the sbt shell JVM were printed to the test output.",
        "The last sbt shell output:",
        sbtShellOutput().takeRight(SbtShellOutputTailLength),
      ).mkString("\n")
      throw new AssertionError(message, result.failed.toOption.orNull)

    result.get

  private def threadDumps(reason: String, shellProcessHandler: OSProcessHandler): String =
    val sbtShellPid = shellProcessHandler.getProcess.pid()
    Seq(
      s"===== $reason: thread dump of the test JVM =====",
      ThreadDumper.dumpThreadsToString(),
      s"===== $reason: thread dump of the sbt shell JVM (pid $sbtShellPid) =====",
      jvmThreadDump(sbtShellPid),
      s"===== $reason: end of the thread dumps =====",
    ).mkString("\n")

  private def jvmThreadDump(pid: Long): String =
    jstackExecutable(pid) match
      case Some(jstack) =>
        Try(ExecUtil.execAndGetOutput(new GeneralCommandLine(jstack.toString, pid.toString), JstackTimeout.toMillis.toInt)).fold(
          exception => s"Couldn't run $jstack: $exception",
          output =>
            if output.isTimeout then s"$jstack didn't finish in $JstackTimeout"
            else Seq(output.getStdout, output.getStderr).filter(_.nonEmpty).mkString("\n"),
        )
      case None =>
        "jstack wasn't found next to the sbt shell JVM or the test JVM"

  /** Prefers the jstack of the sbt shell JVM's own JDK, falling back to the test JVM's. */
  private def jstackExecutable(pid: Long): Option[Path] =
    val executableName = if OS.CURRENT == OS.Windows then "jstack.exe" else "jstack"
    val sbtShellJavaBin = ProcessHandle.of(pid).toScala
      .flatMap(_.info().command().toScala)
      .flatMap(command => Option(Path.of(command).getParent))
    val testJavaBin = Path.of(System.getProperty("java.home"), "bin")
    (sbtShellJavaBin.toSeq :+ testJavaBin)
      .map(_.resolve(executableName))
      .find(Files.isExecutable(_))
