package org.jetbrains.plugins.scala.scalaMeta

import com.intellij.openapi.application.ApplicationManager

trait ScalaMetaApi
  extends QuasiquoteInferUtilApi
  with ScalaMetaParseExceptionApi

object ScalaMetaApi {
  def instance: ScalaMetaApi = ApplicationManager.getApplication.getService(classOf[ScalaMetaApi])
}