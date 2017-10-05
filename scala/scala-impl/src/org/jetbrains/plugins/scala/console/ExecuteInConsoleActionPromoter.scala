package org.jetbrains.plugins.scala
package console

import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase

/**
 * User: Dmitry Naydanov
 * Date: 11/14/13
 */
class ExecuteInConsoleActionPromoter extends SingleActionPromoterBase {
  override def shouldPromote(anAction: AnAction): Boolean = anAction.isInstanceOf[ScalaConsoleExecuteAction]
}
