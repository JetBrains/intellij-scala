package org.jetbrains.sbt
package project.structure

import java.io.File

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.scala.project.template.writeLinesTo

/**
 * @author Pavel Fatin
 */
final class SbtException private(key: String, params: Seq[String])
  extends RuntimeException(SbtBundle.message(key, params))

object SbtException {

  private[this] val WarnRegexp = "^\\[warn]\\s*::\\s*((?!UNRESOLVED DEPENDENCIES).)*".r

  def apply(log: Seq[String]): SbtException = {
    val (key, params) = if (log.exists(_.startsWith("sbt.ResolveException"))) {
      ("sbt.import.unresolvedDependencies", Seq(handleUnresolvedDeps(log), dumpLog(log)))
    } else {
      ("sbt.import.error", Seq(log.mkString(System.getProperty("line.separator"))))
    }

    new SbtException(key, params)
  }

  private[this] def handleUnresolvedDeps(log: Seq[String]) =
    log.foldLeft(StringBuilder.newBuilder) {
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