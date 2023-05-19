package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.scala.extensions.RichFile

import java.io.File
import scala.jdk.CollectionConverters._

/**
  * Support for the .jvmopts file loaded by the sbt launcher script as alternative to command line options.
  */
object JvmOpts {

  def loadFrom(directory: File): Seq[String] = {
    val jvmOptsFile = directory / ".jvmopts"
    if (jvmOptsFile.exists && jvmOptsFile.isFile && jvmOptsFile.canRead) {
      val optsFromFile = FileUtil.loadLines(jvmOptsFile).asScala.toSeq
      processJvmOptions(optsFromFile)
    } else
      Seq.empty
  }

  def processJvmOptions(options: Seq[String]): Seq[String] = {
    options
      .filter(SbtUtil.areQuotesClosedCorrectly)
      .flatMap(ParametersListUtil.parse(_, false, true).asScala.toSeq)
      .filter(_.startsWith("-"))
      .map(_.trim)
  }

}
