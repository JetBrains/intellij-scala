package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.lang.reflect.{Constructor, Method}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ReflectionException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.ReflectUtils._

import scala.util.Try

//noinspection TypeAnnotation,HardCodedStringLiteral
class ScalafmtReflectConfig private[dynamic](
  val fmtReflect: ScalafmtReflect,
  private[dynamic] val target: Object, // real config object
  private val classLoader: ClassLoader
) {

  private val targetCls = target.getClass
  private val constructor: Constructor[_] = targetCls.getConstructors()(0)
  private val constructorParamNames = constructor.getParameters.map(_.getName)
  private val publicMethodNames = targetCls.getMethods.map(_.getName)
  private val rewriteParamIdx = constructorParamNames.indexOf("rewrite").ensuring(_ >= 0)
  private val emptyRewrites = target.invoke("apply$default$" + (rewriteParamIdx + 1))

  private val dialectCls  = classLoader.loadClass("scala.meta.Dialect")
  private val dialectsCls = classLoader.loadClass("scala.meta.dialects.package")

  private val rewriteRulesMethod = Try(targetCls.getMethod("rewrite")).toOption

  // NOTE: since 3.0.0 was renamed from continuationIndent to indent
  private val indentMethod: Option[Method] =
    Try(targetCls.getMethod("continuationIndent"))
      .orElse(Try(targetCls.getMethod("indent"))).toOption

  private val sbtDialect: Object =
    try dialectsCls.invokeStatic("Sbt") catch {
      case ReflectionException(_: NoSuchMethodException) =>
        dialectsCls.invokeStatic("Sbt0137")
    }

  val version: String =
    target.invokeAs[String]("version").trim

  def isIncludedInProject(filename: String): Boolean = {
    val matcher = target.invoke("project").invoke("matcher")
    matcher.invokeAs[java.lang.Boolean]("matches", filename.asParam)
  }

  def withSbtDialect: ScalafmtReflectConfig = {
    // TODO: maybe hold loaded classes in some helper class not to reload them each time?
    val newTarget = target.invoke("withDialect", (dialectCls, sbtDialect))
    new ScalafmtReflectConfig(fmtReflect, newTarget, classLoader)
  }

  // TODO: what about rewrite tokens?
  def hasRewriteRules: Boolean =
    rewriteRulesMethod match {
      case Some(method) =>
        // > v0.4.1
        val rewriteSettings = method.invoke(target)
        !rewriteSettings.invoke("rules").invokeAs[Boolean]("isEmpty")
      case None =>
        false
    }

  def withoutRewriteRules: ScalafmtReflectConfig =
    if (hasRewriteRules) {
      val fieldsValues: Array[Object] = constructorParamNames.zipWithIndex.map { case (param, idx) =>
        // some public case class fields were made deprecated and made private, so we can't access them
        // https://github.com/scalameta/scalafmt/commit/581a99660373554468617b27a349dc732aff92e2
        // https://github.com/scalameta/scalafmt/commit/5bf5fbfc4454131b113731880002f52c725512c1
        val accessorName = if (publicMethodNames.contains(param)) param else param + ("$access$" + idx)
        target.invoke(accessorName)
      }
      fieldsValues(rewriteParamIdx) = emptyRewrites
      val targetNew = constructor.newInstance(fieldsValues: _*).asInstanceOf[Object]
      new ScalafmtReflectConfig(fmtReflect, targetNew, classLoader)
    } else {
      this
    }

  val indents: ScalafmtIndents = ScalafmtIndents(
    indentMain,
    indentCallSite,
    indentDefnSite,
  )

  private def indentMain: Int =
    indentMethod match {
      case Some(method) =>
        val indentsObj = method.invoke(target)
        Try(indentsObj.invokeAs[Int]("main")).recover {
          case _: NoSuchMethodException =>
            ScalafmtIndents.Default.main
        }.get
      case None =>
        ScalafmtIndents.Default.main
    }

  private def indentCallSite: Int =
    indentMethod match {
      case Some(method) =>
        val indentsObj = method.invoke(target)
        indentsObj.invokeAs[Int]("callSite")
      case None =>
        ScalafmtIndents.Default.callSite
    }

  private def indentDefnSite: Int =
    indentMethod match {
      case Some(method) =>
        val indentsObj = method.invoke(target)
        indentsObj.invokeAs[Int]("defnSite")
      case None =>
        ScalafmtIndents.Default.defnSite
    }

  override def equals(obj: Any): Boolean = target.equals(obj)

  override def hashCode(): Int = target.hashCode()
}