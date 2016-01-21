package org.jetbrains.jps.incremental.scala.local

import java.io.{File, OutputStream, PrintStream}

import dotty.tools.dotc.CompilerCallback
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerJars}
import sbt.ClasspathOptions
import sbt.compiler.CompilerArguments
import xsbti.compile.ScalaInstance

/**
  * @author Nikolay.Tropin
  */
class DottyCompiler(scalaInstance: ScalaInstance, compilerJars: CompilerJars) extends Compiler {
  override def compile(compilationData: CompilationData, client: Client): Unit = {
    val cArgs = new CompilerArguments(scalaInstance, ClasspathOptions.javac(compiler = false))
    val scalaOptions = compilationData.scalaOptions.flatMap(splitArg)
    val args: Array[String] =
      cArgs(compilationData.sources, compilationData.classpath,
        Some(compilationData.output), scalaOptions).toArray

    val oldOut = System.out
    System.setOut(emptyPrintStream)

    val mainObj = Class.forName("dotty.tools.dotc.Main$", true, scalaInstance.loader)
    val moduleField = mainObj.getField("MODULE$")
    val mainInstance = moduleField.get(null)

    val process = mainObj.getMethod("process", classOf[Array[String]], classOf[CompilerCallback])

    try {
      process.invoke(mainInstance, args, new ClientDottyCallback(client))
    }
    finally {
      System.setOut(oldOut)
    }
  }


  private val emptyPrintStream = new PrintStream(new OutputStream {
    override def write(b: Int): Unit = {}
  })

  private def splitArg(arg: String): Seq[String] = {
    val colonIdx = arg.indexOf(':')
    if (colonIdx > 0 && colonIdx < arg.length - 1)
      Seq(arg.substring(0, colonIdx + 1).trim, arg.substring(colonIdx + 1).trim)
    else Seq(arg)
  }
}

class ClientDottyCallback(client: Client) extends CompilerCallback {
  override def onClassGenerated(source: File, generatedClass: File, className: String): Unit = {
    client.generated(source, generatedClass, className)
  }

  override def onSourceCompiled(source: File): Unit = {
    client.processed(source)
  }
//
//  override def advance(currentProgress: Int, totalProgress: Int): Unit = {
//    client.progress("", Some(currentProgress.toFloat / totalProgress.toFloat))
//  }
//
//  override def startUnit(phase: String, sourcePath: String): Unit = {
//    val unitName = new File(sourcePath).getName
//    client.progress(s"$unitName (phase $phase)")
//  }
//
//  override def info(msg: String, pos: Position): Unit = client.info(msg, source(pos), line(pos), column(pos))
//
//  override def warning(msg: String, pos: Position): Unit = client.warning(msg, source(pos), line(pos), column(pos))
//
//  override def error(msg: String, pos: Position): Unit = client.error(msg, source(pos), line(pos), column(pos))
//
//  override def trace(exception: Throwable): Unit = client.trace(exception)
//
//  override def debug(msg: String): Unit = client.debug(msg)
//
//  private def source(position: Position) = Option(position).map(_.sourceFile())
//
//  private def line(position: Position) = Option(position).map(_.line().toLong + 1)
//
//  private def column(position: Position) = Option(position).map(_.column().toLong + 1)
}