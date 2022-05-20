package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.project.template.writeLinesTo

import java.io.File

/**
 * @author Pavel Fatin
 */
final class SbtException private(message: String) extends RuntimeException(message)

object SbtException {

  @NonNls private[this] val WarnRegexp = "^\\[warn]\\s*::\\s*((?!UNRESOLVED DEPENDENCIES).)*".r

  def apply(log: Seq[String]): SbtException = {
    val message = if (log.exists(_.startsWith("sbt.ResolveException"))) {
      SbtBundle.message("sbt.import.unresolvedDependencies", handleUnresolvedDeps(log), dumpLog(log))
    } else {
      SbtBundle.message("sbt.import.error", log.mkString(System.getProperty("line.separator")))
    }

    new SbtException(message)
  }

  private[this] def handleUnresolvedDeps(log: Seq[String]) =
    log.foldLeft(new StringBuilder()) {
      case (accumulator, WarnRegexp(group)) => accumulator.append("<li>").append(group).append("</li>")
      case (accumulator, _) => accumulator
    }.toString

  private[this] def dumpLog(log: Seq[String]) = {
    val logDir = new File(PathManager.getLogPath)
    logDir.mkdirs()

    val file = new File(logDir, "sbt.last.log")
    file.createNewFile()

    writeLinesTo(file)(log: _*)
    file.getAbsolutePath
  }
}