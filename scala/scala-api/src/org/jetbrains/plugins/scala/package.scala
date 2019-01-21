package org.jetbrains.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

package object scala {

  def inWriteAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isWriteAccessAllowed => body
    case application => application.runWriteAction(body)
  }

  def inReadAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(body)
  }

  import _root_.scala.language.implicitConversions

  private[this] implicit def toComputable[T](action: => T): Computable[T] = () => action

}
