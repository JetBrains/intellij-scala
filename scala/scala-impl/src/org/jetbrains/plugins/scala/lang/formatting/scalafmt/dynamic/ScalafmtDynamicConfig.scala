package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ReflectionException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.ReflectUtils._

import scala.util.Try

//noinspection TypeAnnotation
class ScalafmtDynamicConfig private[dynamic](val fmtReflect: ScalafmtReflect,
                                             protected[dynamic] val target: Object, // real config object
                                             protected[dynamic] val classLoader: ClassLoader) {

  protected val targetCls = target.getClass
  protected val dialectCls = classLoader.loadClass("scala.meta.Dialect")
  protected val dialectsCls = classLoader.loadClass("scala.meta.dialects.package")

  protected val rewriteRulesMethod = Try(targetCls.getMethod("rewrite")).toOption

  protected val continuationIndentMethod = Try(targetCls.getMethod("continuationIndent")).toOption
  protected val continuationIndentCallSiteMethod = Try(targetCls.getMethod("continuationIndentCallSite")).toOption
  protected val continuationIndentDefnSiteMethod = Try(targetCls.getMethod("continuationIndentDefnSite")).toOption
  protected val DefaultIndentCallSite = 2
  protected val DefaultIndentDefnSite = 4

  protected val sbtDialect: Object = {
    try dialectsCls.invokeStatic("Sbt") catch {
      case ReflectionException(_: NoSuchMethodException) =>
        dialectsCls.invokeStatic("Sbt0137")
    }
  }

  lazy val version: String = {
    target.invokeAs[String]("version").trim
  }

  def isIncludedInProject(filename: String): Boolean = {
    val matcher = target.invoke("project").invoke("matcher")
    matcher.invokeAs[java.lang.Boolean]("matches", filename.asParam)
  }

  def withSbtDialect: ScalafmtDynamicConfig = {
    // TODO: maybe hold loaded classes in some helper class not to reload them each time?
    val newTarget = target.invoke("withDialect", (dialectCls, sbtDialect))
    new ScalafmtDynamicConfig(fmtReflect, newTarget, classLoader)
  }

  // TODO: what about rewrite tokens?
  def hasRewriteRules: Boolean = {
    rewriteRulesMethod match {
      case Some(method) =>
        // > v0.4.1
        val rewriteSettings = method.invoke(target) // TODO: check whether it is correct
        !rewriteSettings.invoke("rules").invokeAs[Boolean]("isEmpty")
      case None =>
        false
    }
  }

  // TODO: check whether it is correct
  def withoutRewriteRules: ScalafmtDynamicConfig = {
    if (hasRewriteRules) {
      this // TODO: support removing of rewrite settings
    } else {
      this
    }
  }

  // TODO: check whether it is correct
  lazy val continuationIndentCallSite: Int = {
    continuationIndentMethod match {
      case Some(method) => // >v0.4
        val indentsObj = method.invoke(target)
        indentsObj.invokeAs[Int]("callSite")
      case None =>
        continuationIndentCallSiteMethod match {
          case Some(method) => // >v0.2.0
            method.invoke(target).asInstanceOf[Int]
          case None =>
            DefaultIndentCallSite
        }
    }
  }

  // TODO: check whether it is correct
  lazy val continuationIndentDefnSite: Int = {
    continuationIndentMethod match {
      case Some(method) =>
        val indentsObj = method.invoke(target)
        indentsObj.invokeAs[Int]("defnSite")
      case None =>
        continuationIndentDefnSiteMethod match {
          case Some(method) =>
            method.invoke(target).asInstanceOf[Int]
          case None =>
            DefaultIndentDefnSite
        }
    }
  }

  override def equals(obj: Any): Boolean = target.equals(obj)

  override def hashCode(): Int = target.hashCode()
}

case class ScalafmtDynamicConfigError(msg: String)