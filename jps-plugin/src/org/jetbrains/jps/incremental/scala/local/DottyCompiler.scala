package org.jetbrains.jps.incremental.scala.local

import java.io.{File, OutputStream, PrintStream}
import java.util.Optional

import dotty.tools.dotc.interfaces._
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerJars}
import sbt.ClasspathOptions
import sbt.compiler.CompilerArguments
import xsbti.compile.ScalaInstance

import scala.collection.mutable
import scala.language.implicitConversions

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

    try {
      System.setOut(emptyPrintStream)

      val mainObj = Class.forName("dotty.tools.dotc.Main$", true, scalaInstance.loader)
      mainObj.getClassLoader
      val moduleField = mainObj.getField("MODULE$")
      val mainInstance = moduleField.get(null)

      client.progress("compiling")

      val process = mainObj.getMethod("process", classOf[Array[String]], classOf[SimpleReporter], classOf[CompilerCallback])
      process.invoke(mainInstance, args, new ClientDottyReporter(client), new ClientDottyCallback(client))
    }
    finally {
      System.setOut(oldOut)
    }
  }


  private val emptyPrintStream = new PrintStream(new OutputStream {
    override def write(b: Int): Unit = {}
  })

  //options for these settings should be in the separate entry in the array of compiler arguments
  val argsToSplit = Set("-target:", "-g:", "-Yresolve-term-conflict:", "-Ylinearizer:", "-Ystruct-dispatch:", "-Ybuilder-debug:")

  private def splitArg(arg: String): Seq[String] = {
    if (!argsToSplit.exists(arg.startsWith)) return Seq(arg)

    val colonIdx = arg.indexOf(':')
    if (colonIdx > 0 && colonIdx < arg.length - 1 && !arg.charAt(colonIdx + 1).isWhitespace)
      Seq(arg.substring(0, colonIdx + 1).trim, arg.substring(colonIdx + 1).trim)
    else Seq(arg)
  }
}

class ClientDottyCallback(client: Client) extends CompilerCallback {
  private def toJFile(f: AbstractFile) = {
    if (f.jfile().isPresent) f.jfile().get()
    else new File(f.path())
  }

  override def onClassGenerated(sourceFile: SourceFile, abstractFile: AbstractFile, s: String): Unit = {
    val source = toJFile(sourceFile)
    val classFile = toJFile(abstractFile)
    client.generated(source, classFile, sourceFile.name())
  }

  override def onSourceCompiled(sourceFile: SourceFile): Unit = {
    val source = toJFile(sourceFile)
    client.processed(source)
  }
}

class ClientDottyReporter(client: Client) extends SimpleReporter {
  private def toOption[T](x: Optional[T]): Option[T] = if (x.isPresent) Some(x.get()) else None

  private val seenMessageHashes = mutable.HashMap[Int, Int]()

  //to show duplicated messages, i.e. results of different compiler phases
  private def unique(s: String): String = {
    val hashCode = s.hashCode
    val seenTimes = seenMessageHashes.getOrElse(hashCode, 0)
    seenMessageHashes.update(hashCode, seenTimes + 1)
    val invisiblePostfix = " " * seenTimes
    s + invisiblePostfix
  }

  override def report(diagnostic: Diagnostic): Unit = {
    val position = toOption(diagnostic.position())
    val file = position.map(_.source().path()).map(new File(_))
    val line = position.map(_.line().toLong + 1)
    val column = position.map(_.column().toLong + 1)
    val message = unique(diagnostic.message())

    diagnostic.level() match {
      case Diagnostic.ERROR =>
        client.error(message, file, line, column)
      case Diagnostic.WARNING =>
        client.warning(message, file, line, column)
      case Diagnostic.INFO =>
        client.info(message, file, line, column)
    }
  }
}