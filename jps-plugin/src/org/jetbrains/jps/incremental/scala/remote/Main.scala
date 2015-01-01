package org.jetbrains.jps.incremental.scala
package remote

import java.io.{File, PrintStream}

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.NGContext
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.{LocalServer, WorksheetInProcessRunnerFactory}

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov         
 */
object Main {
  private val Server = new LocalServer()
  private val worksheetFactory = new WorksheetInProcessRunnerFactory

  def nailMain(context: NGContext) {
    make(context.getArgs.toSeq, context.out, false)
  }
  
  def main(args: Array[String]) {
    make(args, System.out, true)
  }
  
  private def make(arguments: Seq[String], out: PrintStream, standalone: Boolean) {
    var hasErrors = false

    val client = {
      val eventHandler = (event: Event) => {
        val encode = Base64Converter.encode(event.toBytes)
        out.write((if (standalone && !encode.endsWith("=")) encode + "=" else encode).getBytes)
      }
      new EventGeneratingClient(eventHandler, out.checkError) {
        override def error(text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
          hasErrors = true
          super.error(text, source, line, column)
        }

        override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
          if (kind == Kind.ERROR) hasErrors = true
          super.message(kind, text, source, line, column)
        }
      }
    }

    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)
    
    try {
      val args = {
        val strings = arguments.map {
          arg => 
            val s = new String(Base64Converter.decode(arg.getBytes), "UTF-8")
            if (s == "#STUB#") "" else s
        }
        Arguments.from(strings)
      }
      
      Server.compile(args.sbtData, args.compilerData, args.compilationData, client)

      if (!hasErrors) worksheetFactory.getRunner(out, standalone).loadAndRun(args, client)
    } catch {
      case e: Throwable => 
        client.trace(e)
    } finally {
      System.setOut(oldOut)
    }
  }
}
