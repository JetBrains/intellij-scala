package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.structure.SbtOption._

import java.io.File
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
  * Support for the .sbtopts file loaded by the sbt launcher script as alternative to command line options.
  */
object SbtOpts {

  val SbtOptsFile: String = ".sbtopts"

  private val sbtToJdkOpts = (projectPath: String) => ListMap(
    "-sbt-boot" -> JvmOptionGlobal("-Dsbt.boot.directory="),
    "-sbt-dir" -> JvmOptionGlobal("-Dsbt.global.base="),
    "-ivy" -> JvmOptionGlobal("-Dsbt.ivy.home="),
    "-no-global" -> JvmOptionGlobal(s"-Dsbt.global.base=$projectPath/project/.sbtboot"),
    "-no-share" -> JvmOptionGlobal("-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy"),
    "-jvm-debug" -> JvmOptionGlobal("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address="),
    "-sbt-cache" -> JvmOptionGlobal("-Dsbt.global.localcache="),
    "-debug-inc" -> JvmOptionGlobal("-Dxsbt.inc.debug=true"),
    "-traces" -> JvmOptionGlobal("-Dsbt.traces=true"),
    "-timings" -> JvmOptionGlobal("-Dsbt.task.timings=true -Dsbt.task.timings.on.shutdown=true"),
    "-no-colors" -> JvmOptionShellOnly("-Dsbt.log.noformat=true"),
    "-color=" -> JvmOptionShellOnly("-Dsbt.color=")
  )

  private val sbtToLauncherOpts: ListMap[String, SbtOption] = ListMap(
    "-d" -> SbtLauncherOption("--debug"),
    "-debug" -> SbtLauncherOption("--debug"),
    "-warn" -> SbtLauncherOption("--warn"),
    "-info" -> SbtLauncherOption("--info"),
    "-error" -> SbtLauncherOption("--error")
  )

  def loadFrom(directory: File): Seq[SbtOption] = {
    val sbtOptsFile = directory / SbtOptsFile
    if (sbtOptsFile.exists && sbtOptsFile.isFile && sbtOptsFile.canRead) {
      val optsFromFile = FileUtil.loadLines(sbtOptsFile)
        .asScala.iterator
        .map(_.trim)
        .map(removeDoubleDash)
        .toSeq
      processArgs(optsFromFile, directory.getCanonicalPath)
    } else
      Seq.empty
  }

  def combineSbtOptsWithArgs(opts: Seq[String]): Seq[String] = {
    @tailrec
    def prependArgsToOpts(optsToCombine: Seq[String], result: Seq[String]): Seq[String] = {
      def shouldPrepend(opt: String): Boolean = {
        sbtToJdkOpts("").get(opt) match {
          case Some(x) =>
            if (x.value.endsWith("=") && !opt.endsWith("=")) {
              Try(optsToCombine(1)).fold(
                _ => false, // TODO show warning about the lack of the argument
                next =>
                  if (!next.startsWith("-") && next.nonEmpty) true
                  else false // TODO show warning about the incorrect argument
              )
            } else false
          case None => false
        }
      }

      optsToCombine match {
        case h :: _ if shouldPrepend(h) => prependArgsToOpts(optsToCombine.drop(2), result :+ s"$h ${optsToCombine(1)}")
        case h :: t => prependArgsToOpts(t, result :+ h)
        case Nil => result
      }
    }
    prependArgsToOpts(opts.map(removeDoubleDash), Seq.empty)
  }

  def processArgs(opts: Seq[String], projectPath: String): Seq[SbtOption] = {
    opts.flatMap { opt =>
      if (sbtToLauncherOpts.contains(opt))
        sbtToLauncherOpts.get(opt)
      else if (opt.startsWith("-J"))
        Some(JvmOptionGlobal(opt.substring(2)))
      else if (opt.startsWith("-D"))
        Some(JvmOptionGlobal(opt))
      else {
        processOptWithArg(opt, projectPath)
      }
    }
  }

  private def removeDoubleDash(opt: String): String =
    if (opt.startsWith("--") && !opt.matches("\\-+.$")) opt.stripPrefix("-") else opt

  private def processOptWithArg(opt: String, projectPath: String): Option[SbtOption] = {
    sbtToJdkOpts(projectPath)
      .find { case (k, _) => opt.startsWith(k) }
      .flatMap { case (k, x) =>
        val value = opt.replace(k, "")
        val trimmedValue = value.trim
        if (trimmedValue.isEmpty && !x.value.endsWith("=")) Some(x)
        else if (trimmedValue.nonEmpty && x.value.endsWith("=")) {
          if ((!k.endsWith("=") && value.matches("^\\s+.*")) || k.endsWith("=") && value.matches("^[^\\s]+.*")) {
            x match {
              case _: JvmOptionGlobal => Some(JvmOptionGlobal(x.value + trimmedValue))
              case _: JvmOptionShellOnly => Some(JvmOptionShellOnly(x.value + trimmedValue))
              case _ => None
            }
          } else None
        }
        else None
      }
  }
}
