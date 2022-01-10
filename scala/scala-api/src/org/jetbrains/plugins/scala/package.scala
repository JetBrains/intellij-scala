package org.jetbrains.plugins

import com.intellij.openapi.application.ApplicationManager.{getApplication => application}
import _root_.scala.annotation.nowarn

package object scala {

  val ScalaLowerCase = "scala"
  val NotImplementedError = "???"

  def isInternalMode: Boolean = application.isInternal

  def isUnitTestMode: Boolean = application.isUnitTestMode

  private var _possibleSourceTypesCheckIsActive = false

  def possibleSourceTypesCheckIsActive: Boolean = {
    assert(isUnitTestMode, "This property should only be used in unit tests")
    _possibleSourceTypesCheckIsActive
  }

  def withPossibleSourceTypesCheck[T](body: => T): T = {
    assert(!possibleSourceTypesCheckIsActive)
    _possibleSourceTypesCheckIsActive = true
    try body
    finally _possibleSourceTypesCheckIsActive = false
  }

  def inWriteAction[T](body: => T): T = application match {
    case application if application.isWriteAccessAllowed => body
    case application => application.runWriteAction(body)
  }

  def inReadAction[T](body: => T): T = application match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(body)
  }

  import _root_.scala.language.implicitConversions

  @nowarn("cat=deprecation")
  private[this] implicit def toComputable[T](action: => T): com.intellij.openapi.util.Computable[T] = () => action

}
