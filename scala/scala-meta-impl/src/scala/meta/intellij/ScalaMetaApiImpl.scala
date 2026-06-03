package scala.meta.intellij

import org.jetbrains.plugins.scala.scalaMeta.ScalaMetaApi

final class ScalaMetaApiImpl
  extends ScalaMetaApi
  with QuasiquoteInferUtilApiImpl
  with ScalaMetaParseExceptionApiImpl
