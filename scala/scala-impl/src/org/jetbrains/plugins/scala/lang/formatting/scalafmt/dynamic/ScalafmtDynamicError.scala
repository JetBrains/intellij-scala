package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.net.URL
import java.nio.file.Path

import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ScalafmtConfigException

sealed trait ScalafmtDynamicError

object ScalafmtDynamicError {
  case class ConfigDoesNotExist(configPath: Path) extends ScalafmtDynamicError
  case class ConfigMissingVersion(configPath: Path) extends ScalafmtDynamicError
  case class ConfigParseError(configPath: Path, cause: ScalafmtConfigException) extends ScalafmtDynamicError
  case class CannotDownload(version: String, cause: Option[Throwable]) extends ScalafmtDynamicError
  case class InvalidClassPath(version: String, urls: Seq[URL], cause: Throwable) extends ScalafmtDynamicError
  case class UnknownError(message: String, cause: Option[Throwable]) extends ScalafmtDynamicError

  object UnknownError {
    def apply(cause: Throwable): UnknownError = new UnknownError(cause.getMessage, Option(cause))
  }
}
