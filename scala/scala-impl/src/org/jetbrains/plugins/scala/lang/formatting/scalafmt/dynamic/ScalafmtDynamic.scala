package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}

import com.typesafe.config.{ConfigException, ConfigFactory}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamic.{FormatResult, _}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.{DownloadProgressListener, DownloadSuccess}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.{ReflectionException, ScalafmtConfigException, ScalafmtException}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.{BuildInfo, ConsoleScalafmtReporter}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.interfaces.{Scalafmt, ScalafmtReporter}

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Try
import scala.util.control.NonFatal

// TODO: Naumenko: after freezing implementation move this to scalafmt-dynamic project, cover with tests and make pull request
final case class ScalafmtDynamic(reporter: ScalafmtReporter,
                                 respectVersion: Boolean,
                                 respectExcludeFilters: Boolean,
                                 defaultVersion: String,
                                 fmtsCache: mutable.Map[ScalafmtVersion, ScalafmtReflect],
                                 cacheConfigs: Boolean,
                                 configsCache: mutable.Map[Path, (ScalafmtDynamicConfig, FileTime)]) extends Scalafmt {

  def this() = this(
    ConsoleScalafmtReporter,
    true,
    true,
    BuildInfo.stable,
    TrieMap.empty,
    true,
    TrieMap.empty,
  )

  override def clear(): Unit = {
    fmtsCache.values.foreach(_.classLoader.close())
    fmtsCache.clear()
  }

  override def withReporter(reporter: ScalafmtReporter): ScalafmtDynamic =
    copy(reporter = reporter)

  override def withRespectProjectFilters(respectExcludeFilters: Boolean): ScalafmtDynamic =
    copy(respectExcludeFilters = respectExcludeFilters)

  override def withRespectVersion(respectVersion: Boolean): ScalafmtDynamic =
    copy(respectVersion = respectVersion)

  override def withDefaultVersion(defaultVersion: String): ScalafmtDynamic =
    copy(defaultVersion = defaultVersion)

  def withConfigCaching(cacheConfig: Boolean): ScalafmtDynamic =
    copy(cacheConfigs = cacheConfig)

  override def format(config: Path, file: Path, code: String): String = {
    formatDetailed(config, file, code) match {
      case Right(codeFormatted) =>
        codeFormatted
      case Left(error) =>
        reportError(config, file, error)
        code
    }
  }

  private def reportError(config: Path, file: Path, error: ScalafmtDynamicError): Unit = error match {
    case ScalafmtDynamicError.ConfigParseError(configPath, cause) =>
      reporter.error(configPath, cause.getMessage)
    case ScalafmtDynamicError.ConfigDoesNotExist(configPath) =>
      reporter.error(configPath, "file does not exist")
    case ScalafmtDynamicError.ConfigMissingVersion(configPath) =>
      reporter.missingVersion(configPath, defaultVersion)
    case ScalafmtDynamicError.CannotDownload(version, cause) =>
      val message = s"failed to resolve Scalafmt version '$version'"
      cause match {
        case Some(value) =>
          reporter.error(config, ScalafmtException(message, value))
        case None =>
          reporter.error(config, message)
      }
    case ScalafmtDynamicError.UnknownError(_, Some(cause)) =>
      reporter.error(file, cause)
    case ScalafmtDynamicError.UnknownError(message, _) =>
      reporter.error(file, message)
  }

  def formatDetailed(configPath: Path, file: Path, code: String): FormatResult = {
    def tryFormat(reflect: ScalafmtReflect, config: ScalafmtDynamicConfig): FormatResult = {
      Try {
        val filename = file.toString
        val configWithDialect: ScalafmtDynamicConfig =
          if (filename.endsWith(".sbt") || filename.endsWith(".sc")) {
            config.withSbtDialect
          } else {
            config
          }
        if (isIgnoredFile(filename, configWithDialect)) {
          reporter.excluded(file)
          code
        } else {
          reflect.format(code, configWithDialect, Some(file))
        }
      }.toEither.left.map {
        case ReflectionException(e) => ScalafmtDynamicError.UnknownError(e)
        case e => ScalafmtDynamicError.UnknownError(e)
      }
    }

    for {
      config <- resolveConfig(configPath)
      codeFormatted <- tryFormat(config.fmtReflect, config)
    } yield codeFormatted
  }

  private def isIgnoredFile(filename: String, config: ScalafmtDynamicConfig): Boolean = {
    respectExcludeFilters && !config.isIncludedInProject(filename)
  }

  private def resolveConfig(configPath: Path): Either[ScalafmtDynamicError, ScalafmtDynamicConfig] = {
    if (cacheConfigs) {
      val currentTimestamp: FileTime = Files.getLastModifiedTime(configPath)
      configsCache.get(configPath) match {
        case Some((config, lastModified)) if lastModified.compareTo(currentTimestamp) == 0 =>
          Right(config)
        case None =>
          for {
            config <- resolvingConfigDownloading(configPath)
          } yield {
            configsCache(configPath) = (config, currentTimestamp)
            reporter.parsedConfig(configPath, config.version)
            config
          }
      }
    } else {
      resolvingConfigDownloading(configPath)
    }
  }
  private def resolvingConfigDownloading(configPath: Path): Either[ScalafmtDynamicError, ScalafmtDynamicConfig] = {
    for {
      _ <- Option(configPath).filter(conf => Files.exists(conf)).toRight {
        ScalafmtDynamicError.ConfigDoesNotExist(configPath)
      }
      version <- readVersion(configPath).toRight {
        ScalafmtDynamicError.ConfigMissingVersion(configPath)
      }
      fmtReflect <- resolveFormatter(version)
      config <- parseConfig(configPath, fmtReflect)
    } yield config
  }

  private def parseConfig(configPath: Path, fmtReflect: ScalafmtReflect): Either[ScalafmtDynamicError, ScalafmtDynamicConfig] = {
    Try(fmtReflect.parseConfig(configPath)).toEither.left.map {
      case ex: ScalafmtConfigException => ScalafmtDynamicError.ConfigParseError(configPath, ex)
      case ex => ScalafmtDynamicError.UnknownError(ex)
    }
  }

  // NOTE: there can be issues if resolveFormatter is called multiple times (e.g. formatter is called twice)
  //  in such cases download process can be started multiple times,
  //  possible solution: keep information about download process in fmtsCache
  private def resolveFormatter(version: ScalafmtVersion): Either[ScalafmtDynamicError, ScalafmtReflect] = {
    fmtsCache.get(version) match {
      case Some(value) =>
        Right(value)
      case None =>
        val downloadWriter = reporter.downloadWriter()
        val progressListener: DownloadProgressListener = downloadWriter.println _
        val downloader = new ScalafmtDynamicDownloader(progressListener)
        downloader.download(version)
          .left.map(f => ScalafmtDynamicError.CannotDownload(f.version, Some(f.cause)))
          .flatMap(resolveClassPath)
          .map { scalafmt: ScalafmtReflect =>
            fmtsCache(version) = scalafmt
            scalafmt
          }
    }
  }

  private def resolveClassPath(downloadSuccess: DownloadSuccess): Either[ScalafmtDynamicError, ScalafmtReflect] = {
    val DownloadSuccess(version, urls) = downloadSuccess
    Try {
      val classloader = new URLClassLoader(urls, null)
      ScalafmtReflect(
        classloader,
        version,
        respectVersion = true
      )
    }.toEither.left.map {
      case e: ReflectiveOperationException =>
        ScalafmtDynamicError.InvalidClassPath(version, urls, e)
      case e =>
        ScalafmtDynamicError.UnknownError(version, Some(e))
    }
  }

  private def readVersion(config: Path): Option[String] = {
    try Some(ConfigFactory.parseFile(config.toFile).getString("version")) catch {
      case _: ConfigException.Missing if !respectVersion =>
        Some(defaultVersion)
      case NonFatal(_) =>
        None
    }
  }
}

object ScalafmtDynamic {
  type FormatResult = Either[ScalafmtDynamicError, String]
  type ScalafmtVersion = String
}
