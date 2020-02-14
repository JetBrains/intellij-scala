package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{Flushable, PrintStream}
import java.util

import dotty.tools.repl.ReplDriver
import dotty.tools.repl.State
import scala.jdk.CollectionConverters._

import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper3Impl._

/**
 * ATTENTION: when editing ensure to increase the version in ILoopWrapperFactoryHandler
 */
class ILoopWrapper3Impl(
  myOut: PrintStream,
  wrapperReporter: ILoopWrapperReporter, // TODO: use when ReplDriver accepts reporter
  projectFullCp: util.List[String],
  scalaOptions: util.List[String]
) extends ILoopWrapper {

  private var driver: ReplDriverOpen = _
  private var state: State = _

  override def getOutput: Flushable = myOut

  override def init(): Unit = {
    val classPathString: String = projectFullCp.asScala.mkString(";")

    // see dotty.tools.dotc.config.ScalaSettings for the list of possible arguments
    val extraScalaOptions = Seq(
      "-classpath", classPathString,
      "-color:never"
    )
    val scalaOptionsFinal = (scalaOptions.asScala.toSeq ++ extraScalaOptions).distinct
    wrapperReporter.internalDebug(scalaOptionsFinal.mkString("\n####\n"))
    val classLoader: Option[ClassLoader] = None

    driver = new ReplDriverOpen(scalaOptionsFinal.toArray, myOut, classLoader)
    state = driver.initialState
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

object ILoopWrapper3Impl {

  private class ReplDriverOpen(settings: Array[String], out: PrintStream, classLoader: Option[ClassLoader])
    extends ReplDriver(settings, out, classLoader) {

    override def resetToInitial(): Unit = super.resetToInitial()
  }
}