package org.jetbrains.sbt
package project.structure

import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.StreamHasToScala
import scala.util.Using

/**
  * Support for the .jvmopts file loaded by the sbt launcher script as alternative to command line options.
  */
object JvmOpts {

  def loadFrom(directory: Path): Seq[String] = {
    val jvmOptsFile = directory / ".jvmopts"
    if (jvmOptsFile.exists && jvmOptsFile.isRegularFile && Files.isReadable(jvmOptsFile)) {
      val optsFromFile = Using.resource(Files.lines(jvmOptsFile, Charset.defaultCharset()))(_.toScala(Seq))
      processJvmOptions(optsFromFile)
    } else
      Seq.empty
  }

  def processJvmOptions(options: Seq[String]): Seq[String] = {
    options
      .flatMap(SbtUtil.removeCommentedOutPartsAndCheckQuotes)
      .flatMap(ParametersListUtil.parse(_, false, true).asScala.toSeq)
      .filter(_.startsWith("-"))
      .map(_.trim)
  }
}
