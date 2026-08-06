package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface

import java.io.{File, Flushable, PrintWriter}
import scala.annotation.nowarn
import scala.reflect.classTag
import scala.reflect.internal.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.StdReplTags.tagOfIMain
import scala.tools.nsc.interpreter.{ILoop, IMain, NamedParam, ReplReporter, Results}
import scala.collection.JavaConverters._

class ILoopWrapper212_13Impl(
  myOut: PrintWriter,
  wrapperReporter: ILoopWrapperReporter,
  projectFullCp: java.util.List[String],
  scalaOptions: java.util.List[String]
) extends ILoop(None, myOut)
  with ILoopWrapper {

  override def getOutput: Flushable = myOut

  override def init(): Unit = {
    val mySettings = new Settings
    mySettings.processArguments(scalaOptions.asScala.toList, processAll = true)
    mySettings.classpath.value = projectFullCp.asScala.mkString(File.pathSeparator)
    // do not use java class path because it contains scala library jars with version
    // different from one that is used during compilation (it is passed from the plugin classpath)
    mySettings.usejavacp.value = false

    this.settings = mySettings

    createInterpreter()
    intp.initializeSynchronous()
    intp.quietBind(NamedParam[IMain]("$intp", intp)(tagOfIMain, classTag[IMain]))
  }

  // copied from ILoop
  @nowarn("cat=deprecation")
  override def createInterpreter() {
    if (addedClasspath != "")
      settings.classpath.append(addedClasspath)

    intp = new MyILoopInterpreter
  }

  class MyILoopInterpreter extends ILoopInterpreter {

    override lazy val reporter: ReplReporter = new ReplReporter(this) {
      override protected def display(pos: Position, msg: String, severity: Severity): Unit = {
        wrapperReporter.report(severity.toString, pos.line, pos.column, pos.lineContent, msg)
      }
    }
  }

  override def reset(): Unit =
    intp.reset()

  override def shutdown(): Unit =
    closeInterpreter()

  override def processChunk(code: String): Boolean =
    intp.interpret(code) match {
      case Results.Success => true
      case _ => false
    }
}