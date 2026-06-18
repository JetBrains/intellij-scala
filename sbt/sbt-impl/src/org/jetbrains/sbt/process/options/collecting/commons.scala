package org.jetbrains.sbt.process.options.collecting

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util
import scala.jdk.StreamConverters.StreamHasToScala
import scala.util.Using

private def readLinesIfReadable(directory: Path, fileName: String): Seq[String] = {
  val file = directory / fileName
  val canReadFromFile = file.exists && file.isRegularFile && Files.isReadable(file)
  if (canReadFromFile)
    readLines(file)
  else
    Seq.empty
}


private def readLinesIfReadable(file: Path): Seq[String] = {
  val canReadFromFile = file.exists && file.isRegularFile && Files.isReadable(file)
  if (canReadFromFile)
    readLines(file)
  else
    Seq.empty
}

private def readLines(file: Path): Seq[String] =
  Using.resource(Files.lines(file, Charset.defaultCharset()))(_.toScala(Seq))

extension (env: EnvironmentVariablesData) {
  /** Returns a variable value from the same effective environment a process command line would receive. */
  private [collecting] def getEffectiveEnvironmentValue(name: String): Option[String] = {
    val envMap = effectiveEnvironment
    Option(envMap.get(name))
  }

  private def effectiveEnvironment: util.Map[String, String] = {
    // We piggyback on the logic from the GeneralCommandLine to get the effective environment.
    // Unfortunately, the logic is located only there; I couldn't find any more isolated utility
    val commandLine = new GeneralCommandLine()
    env.configureCommandLine(commandLine, true)
    commandLine.getEffectiveEnvironment
  }
}