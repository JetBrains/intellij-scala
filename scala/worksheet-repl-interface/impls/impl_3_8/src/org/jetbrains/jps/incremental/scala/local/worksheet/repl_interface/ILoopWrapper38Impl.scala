package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface

import dotty.tools.repl.{ReplDriver, State}
import org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.ILoopWrapper38Impl.*

import java.io.{File, Flushable, PrintStream}
import java.util
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class ILoopWrapper38Impl(
  myOut: PrintStream,
  wrapperReporter: ILoopWrapperReporter, // TODO: use when ReplDriver accepts reporter
  projectFullCp: util.List[String],
  scalaOptions: util.List[String]
) extends ILoopWrapper {

  private var driver: ReplDriverOpen = uninitialized
  private var state: State = uninitialized

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
    driver.resetToInitial(Nil) // TODO: should we pass any settings here?
    //TODO: ReplDriver has private field `shouldStart` which can set to false if repl driver can't be started
    // we should detect it
    state = driver.initialState
  }

  override def shutdown(): Unit = {
    myOut.flush()
    // TODO: should we cleanup something and how?
  }

  override def processChunk(code: String): Boolean = {
    state = driver.run(code)(using state)
    true // TODO: get the result from the driver when it implements this
  }
}

object ILoopWrapper38Impl {

  private class ReplDriverOpen(settings: Array[String], out: PrintStream, classLoader: Option[ClassLoader])
    extends ReplDriver(settings, out, classLoader) {

    override protected def redirectOutput: Boolean = false

    override def resetToInitial(settings: List[String]): Unit = super.resetToInitial(settings)
  }
}
