package org.jetbrains.bsp.project.importing.setup

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object MillConfigSetup {
  private val MillProcessCheckTimeout = 100.millis

  def apply(baseDir: File): MillConfigSetup = {
    val processBuilder = new ProcessBuilder(Seq("./mill", "-i", "mill.contrib.BSP/install").asJava)
    processBuilder.directory(baseDir)
    new MillConfigSetup(processBuilder)
  }
}

class MillConfigSetup(processBuilder: ProcessBuilder) extends BspConfigSetup {
  import MillConfigSetup._

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  private def waitFinish(process: Process): Option[BuildMessages] =
    Iterator.continually(process.waitFor(MillProcessCheckTimeout.length, MillProcessCheckTimeout.unit))
      .map { end =>
        if (! end && cancellationFlag.get()) {
          process.destroy()
          true
        } else end
      }
      .find(identity)
      .map(_ => BuildMessages.empty.status(BuildMessages.OK))

  override def run(implicit reporter: BuildReporter): Try[BuildMessages] = {
    // TODO send process output to reporter / messages
    reporter.start()
    val process = processBuilder.start()
    val result = Try(waitFinish(process).get)
    result match {
      case Failure(err) => reporter.finishWithFailure(err)
      case Success(bm) => reporter.finish(bm)
    }
    result
  }
}
