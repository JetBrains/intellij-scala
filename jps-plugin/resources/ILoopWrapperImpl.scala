package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, PrintWriter}

import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper

import scala.reflect.classTag
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.StdReplTags.tagOfIMain
import scala.tools.nsc.interpreter.{ILoop, IMain, NamedParam, Results}

/**
  * User: Dmitry.Naydanov
  * Date: 27.01.17.
  */
class ILoopWrapperImpl(out: PrintWriter, projectFullCp: Iterable[String]) extends ILoop(None, out) with ILoopWrapper {
  override def init(): Unit = {
    val mySettings = new Settings
    mySettings.classpath.value = projectFullCp.mkString(File.pathSeparator)
    mySettings.usejavacp.value = true
    
    this.settings = mySettings
    
    createInterpreter()
    intp.initializeSynchronous()
    intp.quietBind(NamedParam[IMain]("$intp", intp)(tagOfIMain, classTag[IMain]))
    intp.setContextClassLoader()
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

  override def getOutputWriter: PrintWriter = out
}
