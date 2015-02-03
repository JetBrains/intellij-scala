package org.jetbrains.sbt
package project.structure

import java.io.File

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel Fatin
 */
class SbtException(message: String) extends Exception(message)

object SbtException {

  val ACCEPTABLE_TO_DISPLAY_LOG_SIZE = 20

  import Utils._

  def fromSbtLog(log: String): SbtException = {
    val lines = log.lines.toSeq

    if (lines.last.contains("unresolved dependency"))
      return handleUnresolvedDeps(lines)

    trimLogIfNecessary(lines) match {
      case NotTrimmed =>
        new SbtException(log)
      case Trimmed(whatsLeft) =>
        new SbtException(SbtBundle("sbt.import.errorLogIsTooLong", whatsLeft,
                          dumpLog(log).getAbsolutePath))
    }
  }

  private def handleUnresolvedDeps(lines: Seq[String]): SbtException = {
    val dependencies = lines.foldLeft("") { (acc, line) =>
      if (line.startsWith("[warn]")) {
        val trimmed = line.substring(6).trim
        if (trimmed.startsWith(":: ") && !trimmed.contains("UNRESOLVED DEPENDENCIES"))
          acc + s"\t${trimmed.substring(2)}\n"
        else
          acc
      } else
        acc
    }
    new SbtException(SbtBundle("sbt.import.unresolvedDependencies", dependencies,
                      dumpLog(joinLines(lines)).getAbsolutePath))
  }

  private object Utils {
    def joinLines(lines: Seq[String]): String =
      lines.mkString(System.getProperty("line.separator"))

    trait TrimResult
    object NotTrimmed extends TrimResult
    case class Trimmed(whatsLeft: String) extends TrimResult

    def trimLogIfNecessary(lines: Seq[String]): TrimResult =
      if (lines.length > ACCEPTABLE_TO_DISPLAY_LOG_SIZE)
        Trimmed(joinLines(lines.takeRight(ACCEPTABLE_TO_DISPLAY_LOG_SIZE)))
      else
        NotTrimmed

    def dumpLog(log: String): File = {
      val file = new File(PathManager.getLogPath, "sbt.last.log")
      file.createNewFile()
      file.write(log)
      file
    }
  }
}