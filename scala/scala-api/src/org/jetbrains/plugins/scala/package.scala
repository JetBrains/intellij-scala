package org.jetbrains.plugins

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager.{getApplication => application}
import com.intellij.openapi.util.Computable
import com.intellij.util.SystemProperties

package object scala {

  val ScalaLowerCase = "scala"

  def isInternalMode: Boolean = application match {
    case null => SystemProperties.is(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY)
    case application => application.isInternal
  }

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

  private[this] implicit def toComputable[T](action: => T): Computable[T] = () => action

}
