package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{PrintStream, Flushable}

import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper3Impl._

import dotty.tools.repl.ReplDriver
import dotty.tools.repl.State

import scala.jdk.CollectionConverters._

/**
 * ATTENTION: when editing ensure to increase the version in ILoopWrapperFactoryHandler
 */
class ILoopWrapper3Impl(
  myOut: PrintStream,
  //wrapperReporter: ILoopWrapperReporter, // TODO: use when ReplDriver accepts reporter
  projectFullCp: java.util.List[String],
  scalaOptions: java.util.List[String]
) extends ILoopWrapper {

  private var driver: ReplDriverOpen = _
  private var state: State = _

  override def getOutput: Flushable = myOut

  override def init(): Unit = {
    val classPathString: String = buildClassPathString(projectFullCp)

    // see dotty.tools.dotc.config.ScalaSettings for the list of possible arguments
    val replArgs = Array(
      "-classpath", classPathString,
      "-color:never"
    )
    val classLoader: Option[ClassLoader] = None

    driver = new ReplDriverOpen(replArgs, myOut, classLoader)
    state = driver.initialState
  }

  private def buildClassPathString(classpathList: java.util.List[String]): String = {
    val classpathBuilder = new java.lang.StringBuilder
    classpathList.forEach { path =>
      classpathBuilder.append(path)
      classpathBuilder.append(";")
    }
    classpathBuilder.toString
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
    true // TODO: get the result from the driver
  }
}

object ILoopWrapper3Impl {

  private class ReplDriverOpen(settings: Array[String], out: PrintStream, classLoader: Option[ClassLoader])
    extends ReplDriver(settings, out, classLoader) {

    override def resetToInitial(): Unit = super.resetToInitial()
  }
}