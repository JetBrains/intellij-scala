package org.jetbrains.sbt
package project.structure

import java.io.File
import scala.collection.JavaConverters._

import com.intellij.openapi.util.io.FileUtil

/**
 * @author Nikolay Obedin
 * @since 11/27/14.
 */
object SbtOpts {

  def loadFrom(directory: File): Seq[String] = {
    val sbtOptsFile = directory / ".sbtopts"
    if (sbtOptsFile.exists && sbtOptsFile.isFile && sbtOptsFile.canRead)
      process(FileUtil.loadLines(sbtOptsFile).asScala.map(_.trim))
    else
      Seq.empty
  }

  private val noShareOpts  = "-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy"
  private val noGlobalOpts = "-Dsbt.global.base=project/.sbtboot"
  private val debuggerOpts = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address="

  private val sbtToJdkOpts: Map[String, String] = Map(
    "-sbt-boot" -> "-Dsbt.boot.directory=",
    "-sbt-dir" -> "-Dsbt.global.base=",
    "-ivy" -> "-Dsbt.ivy.home=",
    "-jvm-debug" -> debuggerOpts
  )

  private def process(opts: Seq[String]): Seq[String] = {
    opts.flatMap { opt =>
      if (opt.startsWith("-no-share"))
        Some(noShareOpts)
      else if (opt.startsWith("-no-global"))
        Some(noGlobalOpts)
      else if (sbtToJdkOpts.exists { case (k,_) => opt.startsWith(k) })
        processOptWithArg(opt)
      else if (opt.startsWith("-J"))
        Some(opt.substring(2))
      else if (opt.startsWith("-D"))
        Some(opt)
      else
        None
    }
  }

  private def processOptWithArg(opt: String): Option[String] = {
    sbtToJdkOpts.find{ case (k,_) => opt.startsWith(k)}.flatMap { case (k,x) =>
      val v = opt.replace(k, "").trim
      if (v.isEmpty) None else Some(x + v)
    }
  }
}
