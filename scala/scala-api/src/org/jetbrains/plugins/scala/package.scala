package org.jetbrains.plugins

import com.intellij.openapi.application.ApplicationManager.{getApplication => application}
import _root_.scala.annotation.nowarn

package object scala {

  val ScalaLowerCase = "scala"
  val NotImplementedError = "???"

  def isInternalMode: Boolean = application.isInternal

  def isUnitTestMode: Boolean = application.isUnitTestMode

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
