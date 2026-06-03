package org.jetbrains.plugins

import com.intellij.openapi.application.ApplicationManager.{getApplication => application}

package object scala {

  val ScalaLowerCase = "scala"
  val NotImplementedError = "???"

  def isInternalMode: Boolean = application.isInternal

  def isUnitTestMode: Boolean = application.isUnitTestMode
}
