package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.text.EditDistance
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.structure.SbtOption._

import java.io.File
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
  * Support for the .sbtopts file loaded by the sbt launcher script as alternative to command line options.
  */
object SbtOpts {

  val SbtOptsFile: String = ".sbtopts"

  private def sbtToJdkOpts(projectPath: String) = ListMap(
    "-sbt-boot" -> JvmOptionGlobal("-Dsbt.boot.directory=")("--sbt-boot <path>"),
    "-sbt-dir" -> JvmOptionGlobal("-Dsbt.global.base=")("--sbt-dir <path>"),
    "-ivy" -> JvmOptionGlobal("-Dsbt.ivy.home=")("--ivy <path>"),
    "-no-global" -> JvmOptionGlobal(s"-Dsbt.global.base=$projectPath/project/.sbtboot")("--no-global"),
    "-no-share" -> JvmOptionGlobal("-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy")("--no-share"),
    "-jvm-debug" -> JvmOptionGlobal("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=")("--jvm-debug <port>"),
    "-sbt-cache" -> JvmOptionGlobal("-Dsbt.global.localcache=")("--sbt-cache <path>"),
    "-debug-inc" -> JvmOptionGlobal("-Dxsbt.inc.debug=true")("--debug-inc"),
    "-traces" -> JvmOptionGlobal("-Dsbt.traces=true")("--traces"),
    "-timings" -> JvmOptionGlobal("-Dsbt.task.timings=true -Dsbt.task.timings.on.shutdown=true")("--timings"),
    "-no-colors" -> JvmOptionShellOnly("-Dsbt.log.noformat=true")("--no-colors"),
    "-color=" -> JvmOptionShellOnly("-Dsbt.color=")("--color=auto|always|true|false|never")
  )

  // options proceeded by -- will be also handled (except -d)
  private val sbtToLauncherOpts = ListMap(
    "-d" -> SbtLauncherOption("--debug")("-d"),
    "-debug" -> SbtLauncherOption("--debug")("--debug"),
    "-warn" -> SbtLauncherOption("--warn")("--warn"),
    "-info" -> SbtLauncherOption("--info")("--info"),
    "-error" -> SbtLauncherOption("--error")("--error")
  )

  private val allAvailableOptions = sbtToJdkOpts("") ++ sbtToLauncherOpts

  def loadFrom(directory: File)(implicit reporter: BuildReporter = null): Seq[SbtOption] = {
    val sbtOptsFile = directory / SbtOptsFile
    if (sbtOptsFile.exists && sbtOptsFile.isFile && sbtOptsFile.canRead) {
      val optsFromFile = FileUtil.loadLines(sbtOptsFile)
        .asScala.iterator
        .map(_.trim)
        .filter(_.nonEmpty)
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
                _ => false,
                next =>
                  if (!next.startsWith("-") && next.nonEmpty) true
                  else false
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

  def processArgs(opts: Seq[String], projectPath: String)(implicit reporter: BuildReporter = null): Seq[SbtOption] = {
    val unrecognizedOpts = ListBuffer[(String, Option[String])]()
    val sbtOpts = opts.flatMap { opt =>
      if (sbtToLauncherOpts.contains(opt))
        sbtToLauncherOpts.get(opt)
      else if (opt.startsWith("-J"))
        Some(JvmOptionGlobal(opt.substring(2))())
      else if (opt.startsWith("-D"))
        Some(JvmOptionGlobal(opt)())
      else {
        processOptWithArg(opt, projectPath)
          .orElse {
            unrecognizedOpts.addOne((opt, findClosestOptionHelper(opt)))
            None
          }
      }
    }
    if (unrecognizedOpts.nonEmpty && reporter != null) reportUnrecognizedOptions(unrecognizedOpts.toList)
    sbtOpts
  }

  private def reportUnrecognizedOptions(unrecognizedOpts: List[(String, Option[String])])(implicit reporter: BuildReporter): Unit = {
    val warningDetails = unrecognizedOpts.map {
      case (x, Some(y)) => SbtBundle.message("sbt.unrecognized.opt.with.suggestion", x, y)
      case (x, None) => SbtBundle.message("sbt.unrecognised.opt", x)
    }
    reporter.warning(
      SbtBundle.message("sbt.unrecognized.opts", unrecognizedOpts.size, unrecognizedOpts.map(_._1).mkString(", ")), None,
      (warningDetails :+ SbtBundle.message("sbt.available.opts", allAvailableOptions.map(_._2.helperMsg).mkString("\n", "\n", ""))).mkString("\n"))
  }

  private def findClosestOptionHelper(userOpt: String): Option[String] = {
    val closestOption = allAvailableOptions
      .map { case (optionKey, targetSbtOption) =>
        val optionKeyEndsWithEqualsSign = optionKey.endsWith("=")
        val targetOptionRequiresValue = targetSbtOption.value.endsWith("=")
        val truncatedOptFromArg =
          if (targetOptionRequiresValue && !optionKeyEndsWithEqualsSign) userOpt.split(' ')(0)
          else if (targetOptionRequiresValue && optionKeyEndsWithEqualsSign) userOpt.split("=")(0)
          else userOpt
        val distance = EditDistance.optimalAlignment(optionKey, truncatedOptFromArg.trim, false, 2)
        optionKey -> distance
      }
      .filter(_._2 <= 2)
      .minByOption(_._2)
    closestOption.map{ x => allAvailableOptions(x._1).helperMsg }
  }

  private def isShortOption(opt: String): Boolean = opt.matches("\\-+.$")

  private def removeDoubleDash(opt: String): String =
    if (opt.startsWith("--") && !isShortOption(opt)) opt.stripPrefix("-") else opt

  private def processOptWithArg(option: String, projectPath: String): Option[SbtOption] = {
    sbtToJdkOpts(projectPath)
      .find { case (optionKey, _) => option.startsWith(optionKey) }
      .flatMap { case (optionKey, targetSbtOption) =>
        val optionValue = option.replace(optionKey, "")
        val isOptionValueEmpty = optionValue.trim.isEmpty
        val targetOptionRequiresValue = targetSbtOption.value.endsWith("=")
        if (isOptionValueEmpty && !targetOptionRequiresValue) Some(targetSbtOption)
        else if (!isOptionValueEmpty && targetOptionRequiresValue) {
          val optionKeyEndsWithEqualsSign = optionKey.endsWith("=")
          if ((!optionKeyEndsWithEqualsSign && optionValue.matches("^\\s+.*")) || optionKeyEndsWithEqualsSign && optionValue.matches("^[^\\s]+.*")) {
            targetSbtOption match {
              case _: JvmOptionGlobal => Some(JvmOptionGlobal(targetSbtOption.value + optionValue.trim)())
              case _: JvmOptionShellOnly => Some(JvmOptionShellOnly(targetSbtOption.value + optionValue.trim)())
              case _ => None
            }
          } else None
        }
        else None
      }
  }
}
