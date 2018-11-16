package org.jetbrains.jps.incremental.scala.local.worksheet


import java.io.{File, PrintWriter}

import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper213Impl.DummyConfig

import scala.reflect.classTag
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.StdReplTags.tagOfIMain
import scala.tools.nsc.interpreter.{IMain, NamedParam, Results}
import NamedParam.Typed
import scala.tools.nsc.interpreter.shell.{ILoop, ShellConfig}
import scala.collection.JavaConverters._

/**
  * User: Dmitry.Naydanov
  */
class ILoopWrapper213Impl(myOut: PrintWriter, projectFullCp: java.util.List[String]) extends ILoop(new DummyConfig, out = myOut) with ILoopWrapper {
  override def init(): Unit = {
    val mySettings = new Settings
    mySettings.classpath.append(projectFullCp.asScala.mkString(File.pathSeparator))
    mySettings.usejavacp.tryToSet(List.empty)

    createInterpreter(mySettings)

    val itp = intp.asInstanceOf[IMain]
    itp.initializeCompiler()
    itp.quietBind(new Typed[IMain]("$intp", itp)(tagOfIMain, classTag[IMain]))
    itp.setContextClassLoader()
  }

  override def reset(): Unit = {
    intp.reset()
  }

  override def shutdown(): Unit = {
    closeInterpreter()
  }

  override def processChunk(code: String): Boolean = {
    intp.interpret(code) match {
      case Results.Success => true
      case _ => false
    }
  }

  override def getOutputWriter: PrintWriter = myOut
}

object ILoopWrapper213Impl {
  class DummyConfig extends ShellConfig {
    override def filesToPaste: List[String] = List.empty

    override def filesToLoad: List[String] = List.empty

    override def batchText: String = ""

    override def batchMode: Boolean = false

    override def doCompletion: Boolean = false

    override def haveInteractiveConsole: Boolean = false
  }
}