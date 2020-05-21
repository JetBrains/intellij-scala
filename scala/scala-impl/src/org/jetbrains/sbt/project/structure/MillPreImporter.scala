package org.jetbrains.sbt.project.structure

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object MillPreImporter {
  private val MillProcessCheckTimeout = 100.millis

  def setupBsp(baseDir: File): MillPreImporter = {
    val processBuilder = new ProcessBuilder(Seq("./mill", "-i", "mill.contrib.BSP/install").asJava)
    processBuilder.directory(baseDir)
    new MillPreImporter(processBuilder.start())
  }
}

class MillPreImporter(process: Process) extends Cancellable {
  import MillPreImporter._

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  def waitFinish(): Unit =
    Iterator.continually(process.waitFor(MillProcessCheckTimeout.length, MillProcessCheckTimeout.unit))
      .takeWhile(processEnded => !processEnded && !cancellationFlag.get())
      .toList
}
