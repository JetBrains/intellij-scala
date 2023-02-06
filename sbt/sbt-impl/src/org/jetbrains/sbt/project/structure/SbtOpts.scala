package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.extensions.RichFile

import java.io.File
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

/**
  * Support for the .sbtopts file loaded by the sbt launcher script as alternative to command line options.
  */
object SbtOpts {

  val SbtOptsFile: String = ".sbtopts"

  def loadFrom(directory: File): Seq[CommandOption] = {
    val sbtOptsFile = directory / SbtOptsFile
    if (sbtOptsFile.exists && sbtOptsFile.isFile && sbtOptsFile.canRead)
      processArgs(FileUtil.loadLines(sbtOptsFile)
        .asScala.iterator
        .map(_.trim)
        .toSeq)
    else
      Seq.empty
  }

  sealed abstract class CommandOption(val value: String)
  sealed abstract class JvmOption(override val value: String) extends CommandOption(value)
  case class JvmOptionGlobal(override val value: String) extends JvmOption(value)
  case class JvmOptionShellOnly(override val value: String) extends JvmOption(value)
  case class SbtLauncherOption(override val value: String) extends CommandOption(value)

  private val sbtToJdkOpts: ListMap[String, CommandOption] = ListMap(
    "-sbt-boot" -> JvmOptionGlobal("-Dsbt.boot.directory="),
    "-sbt-dir" -> JvmOptionGlobal("-Dsbt.global.base="),
    "-ivy" -> JvmOptionGlobal("-Dsbt.ivy.home="),
    "-no-global" -> JvmOptionGlobal("-Dsbt.global.base=project/.sbtboot"),
    "-no-share" -> JvmOptionGlobal("-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy"),
    "-jvm-debug" -> JvmOptionGlobal("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address="),
    "-sbt-cache" -> JvmOptionGlobal("-Dsbt.global.localcache="),
    "-debug-inc" -> JvmOptionGlobal("-Dxsbt.inc.debug=true"),
    "-traces" -> JvmOptionGlobal("-Dsbt.traces=true"),

    "-no-colors" -> JvmOptionShellOnly("-Dsbt.log.noformat=true"),
    "-timings" -> JvmOptionGlobal("-Dsbt.task.timings=true -Dsbt.task.timings.on.shutdown=true"),
    "-color" -> JvmOptionShellOnly("-Dsbt.color="),
  )

  private val sbtToLauncherOpts: ListMap[String, CommandOption] = ListMap(
    "-d" -> SbtLauncherOption("--debug"),
    "-debug" -> SbtLauncherOption("--debug"),
    "--debug" -> SbtLauncherOption("--debug"),
    "--warn" -> SbtLauncherOption("--warn"),
    "--info" -> SbtLauncherOption("--info"),
    "--error" -> SbtLauncherOption("--error")
  )

  def processArgs(opts: Seq[String]): Seq[CommandOption] = {
    opts.flatMap { opt =>
      if (sbtToLauncherOpts.contains(opt))
        sbtToLauncherOpts.get(opt)
      else if (opt.startsWith("-J"))
        Some(JvmOptionGlobal(opt.substring(2)))
      else if (opt.startsWith("-D"))
        Some(JvmOptionGlobal(opt))
      else {
        val fixedOpt =
          if (opt.startsWith("--")) opt.stripPrefix("-")
          else opt
        processOptWithArg(fixedOpt)
      }
    }
  }

  private def processOptWithArg(opt: String): Option[CommandOption] = {
    sbtToJdkOpts.find{ case (k,_) => opt.startsWith(k)}.flatMap { case (k,x) =>
      val v = opt.replace(k, "").trim.stripPrefix("=")
      if (v.isEmpty && !x.value.endsWith("=")) Some(x)
      else if (v.nonEmpty && x.value.endsWith("=")) {
        x match {
          case _: JvmOptionGlobal => Some(JvmOptionGlobal(x.value + v))
          case _: JvmOptionShellOnly => Some(JvmOptionShellOnly(x.value + v))
          case _ => None
        }
      }
      else None
    }
  }
}
