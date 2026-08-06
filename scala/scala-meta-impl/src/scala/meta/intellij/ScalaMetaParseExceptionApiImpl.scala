package scala.meta.intellij

import org.jetbrains.plugins.scala.scalaMeta.ScalaMetaParseExceptionApi

trait ScalaMetaParseExceptionApiImpl extends ScalaMetaParseExceptionApi {
  override def extractScalaMetaParseException(throwable: Throwable): Option[(String, Int)] =
    throwable match {
      case e: scala.meta.ParseException => Some((e.getMessage, e.pos.start))
      case _ => None
    }
}
