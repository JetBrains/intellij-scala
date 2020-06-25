package org.jetbrains.jps.incremental.scala.local.worksheet.util

import java.io.{OutputStream, PrintStream}

import com.martiansoftware.nailgun.ThreadLocalPrintStream

private[worksheet] object IOUtils {

  def patchSystemOut(out: OutputStream): Unit = {
    val printStream = new PrintStream(out)
    System.out match {
      case threadLocal: ThreadLocalPrintStream => threadLocal.init(printStream)
      case _                                   => System.setOut(printStream)
    }
  }
}
