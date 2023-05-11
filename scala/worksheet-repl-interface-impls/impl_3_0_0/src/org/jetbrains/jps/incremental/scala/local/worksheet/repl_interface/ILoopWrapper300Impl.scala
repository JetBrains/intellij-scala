package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface

import java.io.{File, Flushable, PrintStream}
import java.util

import org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.ILoopWrapper300Impl._

import dotty.tools.repl.ReplDriver
import dotty.tools.repl.State

import scala.jdk.CollectionConverters._

class ILoopWrapper300Impl(
  myOut: PrintStream,
  wrapperReporter: ILoopWrapperReporter, // TODO: use when ReplDriver accepts reporter
  projectFullCp: util.List[String],
  scalaOptions: util.List[String]
) extends ILoopWrapper {

  private var driver: ReplDriverOpen = _
  private var state: State = _

  override def getOutput: Flushable = myOut

  override def init(): Unit = {
    val classPathString: String = projectFullCp.asScala.mkString(pathSeparator)

    // see dotty.tools.dotc.config.ScalaSettings for the list of possible arguments
    val extraScalaOptions = Seq(
      "-classpath", classPathString
    )
    val scalaOptionsFinal = (scalaOptions.asScala.toSeq ++ extraScalaOptions).distinct.toArray

    val classLoader: Option[ClassLoader] = None

    driver = new ReplDriverOpen(scalaOptionsFinal, myOut, classLoader)
    state = driver.initialState
  }

  private def pathSeparator: String = {
    val separator = Option(System.getProperty("path.separator"))
    separator.getOrElse(fallbackPathSeparator)
  }

  private def fallbackPathSeparator: String =
    File.separator match {
      case "\\" => ";"
      case _    => ":"
    }

  override def reset(): Unit = {
    driver.resetToInitial()
    state = driver.initialState
  }

  override def shutdown(): Unit = {
    myOut.flush()
    // TODO: should we cleanup something and how?
  }

  override def processChunk(code: String): Boolean = {
    state = driver.run(code)(state)
    true // TODO: get the result from the driver when it implements this
  }
}

object ILoopWrapper300Impl {

  private class ReplDriverOpen(settings: Array[String], out: PrintStream, classLoader: Option[ClassLoader])
    extends ReplDriver(settings, out, classLoader) {

    override def resetToInitial(): Unit = super.resetToInitial()
  }
}