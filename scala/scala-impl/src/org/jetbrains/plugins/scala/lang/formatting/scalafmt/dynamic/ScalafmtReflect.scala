package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.ReflectUtils._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.interfaces.ScalafmtReporter

import scala.util.Try

//noinspection TypeAnnotation
case class ScalafmtReflect(classLoader: URLClassLoader,
                           version: String,
                           respectVersion: Boolean,
                           reporter: ScalafmtReporter) {

  import classLoader.loadClass

  protected val formattedCls = loadClass("org.scalafmt.Formatted")
  protected val scalaSetCls = loadClass("scala.collection.immutable.Set")
  protected val optionCls = loadClass("scala.Option")
  protected val configCls = loadClass("org.scalafmt.config.Config")
  protected val scalafmtCls = loadClass("org.scalafmt.Scalafmt")

  protected val parseExceptionCls = loadClass("scala.meta.parsers.ParseException")
  protected val tokenizeExceptionCls = loadClass("scala.meta.tokenizers.TokenizeException")

  protected val defaultScalaFmtConfig = scalafmtCls.invokeStatic("format$default$2")
  protected val emptyRange = scalafmtCls.invokeStatic("format$default$3")

  protected val formattedGet = formattedCls.getMethod("get")

  protected val formatMethod =
    scalafmtCls.getMethod("format", classOf[String], defaultScalaFmtConfig.getClass, scalaSetCls)
  protected val formatMethodWithFilename = Try(
    scalafmtCls.getMethod("format", classOf[String], defaultScalaFmtConfig.getClass, scalaSetCls, classOf[String])
  ).toOption

  lazy val intellijScalaFmtConfig: ScalafmtDynamicConfig = {
    // TODO: see implementation details for other versions of scalafmt, find where intellij config is kept
    assert(version == "1.5.1", "intellij scalafmt config is only supported fot version 1.5.1 for now")

    val scalaFmtConfigCls = classLoader.loadClass("org.scalafmt.config.ScalafmtConfig")
    val configTarget = scalaFmtConfigCls.invokeStatic("intellij")
    new ScalafmtDynamicConfig(this, configTarget, classLoader)
  }

  def parseConfig(configPath: Path): ScalafmtDynamicConfig = {
    val configText = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8)
    parseConfigFromString(configText)
  }

  def parseConfigFromString(configText: String): ScalafmtDynamicConfig = {
    val configured: Object = try { // scalafmt >= 1.6.0
      scalafmtCls.invokeStatic("parseHoconConfig", configText.asParam)
    } catch {
      case _: NoSuchMethodException =>
        // scalafmt >= v0.7.0-RC1 && scalafmt < 1.6.0
        val fromHoconEmptyPath = configCls.invokeStatic("fromHoconString$default$2")
        configCls.invokeStatic("fromHoconString", configText.asParam, (optionCls, fromHoconEmptyPath))
    }

    try {
      new ScalafmtDynamicConfig(this, configured.invoke("get"), classLoader)
    } catch {
      case ReflectionException(e) =>
        throw ScalafmtConfigException(e.getMessage)
    }
  }

  def format(code: String, config: ScalafmtDynamicConfig): String = {
    format(None, code, config)
  }

  def format(fileOpt: Option[Path], code: String, config: ScalafmtDynamicConfig): String = {
    checkVersionMismatch(config)
    val formatted = (formatMethodWithFilename, fileOpt) match {
      case (Some(method), Some(file)) =>
        val filename = file.toString
        method.invoke(null, code, config.target, emptyRange, filename)
      case _ =>
        formatMethod.invoke(null, code, config.target, emptyRange)
    }
    clearTokenizerCache()
    try {
      formattedGet.invoke(formatted).asInstanceOf[String]
    } catch {
      case ReflectionException(e)
        if tokenizeExceptionCls.isInstance(e) ||
          parseExceptionCls.isInstance(e) =>
        val pos = e.invoke("pos")
        val range = positionRange(pos)
        val shortMessage = e.invokeAs[String]("shortMessage")
        throw PositionExceptionImpl(fileOpt, code, shortMessage, e.getMessage, range, e)
    }
  }

  private def positionRange(pos: Object): RangePosition = {
    try {
      RangePosition(
        pos.invokeAs[Int]("startLine"),
        pos.invokeAs[Int]("startColumn"),
        pos.invokeAs[Int]("endLine"),
        pos.invokeAs[Int]("endColumn")
      )
    } catch {
      case _: NoSuchMethodException =>
        val start = pos.invoke("start")
        val end = pos.invoke("end")
        RangePosition(
          start.invokeAs[Int]("line"),
          start.invokeAs[Int]("column"),
          end.invokeAs[Int]("line"),
          end.invokeAs[Int]("column")
        )
    }
  }

  private def clearTokenizerCache(): Unit = {
    val cache = moduleInstance(
      "scala.meta.internal.tokenizers.PlatformTokenizerCache$"
    )
    cache.invoke("megaCache").invoke("clear")
  }

  private def checkVersionMismatch(config: ScalafmtDynamicConfig): Unit = {
    if (respectVersion) {
      val obtained = config.version
      if (obtained != version) {
        throw VersionMismatch(obtained, version)
      }
    }
  }

  private def moduleInstance(fqn: String): Object = {
    val cls = classLoader.loadClass(fqn)
    val module = cls.getField("MODULE$")
    module.setAccessible(true)
    module.get(null)
  }
}
