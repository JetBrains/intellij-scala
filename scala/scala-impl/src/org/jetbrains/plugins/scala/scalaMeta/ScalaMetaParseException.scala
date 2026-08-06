package org.jetbrains.plugins.scala.scalaMeta

trait ScalaMetaParseExceptionApi {
  def extractScalaMetaParseException(throwable: Throwable): Option[(String, Int)]
}

object ScalaMetaParseException {
  def unapply(throwable: Throwable): Option[(String, Int)] =
    ScalaMetaApi.instance.extractScalaMetaParseException(throwable)
}
