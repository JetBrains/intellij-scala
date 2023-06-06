package org.jetbrains.plugins

import com.intellij.openapi.application.ApplicationManager.{getApplication => application}

package object scala {

  val ScalaLowerCase = "scala"
  val NotImplementedError = "???"

  def isInternalMode: Boolean = application.isInternal

  def isUnitTestMode: Boolean = application.isUnitTestMode

  //TODO: deduplicate utilities with org.jetbrains.plugins.scala.extensions#inWriteAction
  // This should be done within a bigger refactor of all our utilities
  def inWriteAction[T](body: => T): T = application match {
    case application if application.isWriteAccessAllowed => body
    case application => application.runWriteAction(body)
  }

  def inReadAction[T](body: => T): T = application match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(body)
  }

  import _root_.scala.language.implicitConversions

  private[this] implicit def toComputable[T](action: => T): com.intellij.openapi.util.Computable[T] = () => action

}
