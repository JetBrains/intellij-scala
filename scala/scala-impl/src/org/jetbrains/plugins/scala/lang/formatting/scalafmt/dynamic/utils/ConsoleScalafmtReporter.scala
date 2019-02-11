package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils

import java.io.{PrintStream, PrintWriter}
import java.nio.file.Path

import org.jetbrains.plugins.scala.lang.formatting.scalafmt.interfaces.ScalafmtReporter

object ConsoleScalafmtReporter extends ConsoleScalafmtReporter(System.err)

class ConsoleScalafmtReporter(out: PrintStream) extends ScalafmtReporter {
  override def error(file: Path, e: Throwable): Unit = {
    out.print(s"error: $file: ")
    trimStacktrace(e)
    e.printStackTrace(out)
  }

  override def error(path: Path, message: String): Unit = {
    out.println(s"error: $path: $message")
  }

  override def excluded(filename: Path): Unit = {
    out.println(s"file excluded: $filename")
  }

  override def parsedConfig(config: Path, scalafmtVersion: String): Unit = {
    out.println(s"parsed config (v$scalafmtVersion): $config")
  }

  override def missingVersion(config: Path, defaultVersion: String): Unit = {
    val message = String.format(
      "missing setting 'version'. To fix this problem, add the following line to .scalafmt.conf: 'version=%s'.",
      defaultVersion
    )
    error(config, message)
  }

  override def downloadWriter(): PrintWriter = new PrintWriter(out)

  protected def trimStacktrace(e: Throwable): Unit = ()
}
