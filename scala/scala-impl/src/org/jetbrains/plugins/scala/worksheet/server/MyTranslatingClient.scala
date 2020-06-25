package org.jetbrains.plugins.scala.worksheet.server

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetDefaultSourcePreprocessor
import org.jetbrains.plugins.scala.worksheet.server.MyTranslatingClient.Log
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.CompilerInterface

private class MyTranslatingClient(
  project: Project,
  worksheet: VirtualFile,
  consumer: CompilerInterface
) extends DummyClient {
  private val endMarker = WorksheetDefaultSourcePreprocessor.ServiceMarkers.END_GENERATED_MARKER

  private def testLog(text: String, e: Throwable): Unit =
    e.printStackTrace()

  override def progress(text: String, done: Option[Float]): Unit =
    consumer.progress(text, done)

  override def trace(exception: Throwable): Unit =
    consumer.trace(exception)

  override def internalDebug(text: String): Unit =
    Log.debugSafe(text)

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, _, PosInfo(line, column, _), _) = msg
    val lines = (if (text == null) "" else text).split("\n")
    val linesLength = lines.length

    val differ = if (linesLength > 2) {
      val endLineIdx = lines(linesLength - 2).indexOf(endMarker)
      if (endLineIdx != -1) {
        endLineIdx + endMarker.length
      } else 0
    } else 0

    val finalText = if (differ == 0) text else {
      val buffer = new StringBuilder

      for (j <- 0 until (linesLength - 2)) {
        buffer.append(lines(j)).append("\n")
      }

      val lines1 = lines(linesLength - 1)

      buffer
        .append(lines(linesLength - 2).substring(differ)).append("\n")
        .append(if (lines1.length > differ) lines1.substring(differ) else lines1).append("\n")

      buffer.toString()
    }

    // TODO: current line & column calculation are broken
    val line1 = line.map(i => i - 4).map(_.toInt).getOrElse(-1)
    val column1 = column.map(_ - differ).map(_.toInt).getOrElse(-1)

    val category = toCompilerMessageCategory(kind)

    val message = new CompilerMessageImpl(project, category, finalText, worksheet, line1, column1, null)
    consumer.message(message)
  }

  private def toCompilerMessageCategory(kind: Kind): CompilerMessageCategory = {
    import BuildMessage.Kind._
    kind match {
      case INFO | JPS_INFO | OTHER        => CompilerMessageCategory.INFORMATION
      case ERROR | INTERNAL_BUILDER_ERROR => CompilerMessageCategory.ERROR
      case PROGRESS                       => CompilerMessageCategory.STATISTICS
      case WARNING                        => CompilerMessageCategory.WARNING
    }
  }

  override def worksheetOutput(text: String): Unit =
    consumer.worksheetOutput(text)
}

object MyTranslatingClient {

  private val Log = Logger.getInstance(this.getClass)
}