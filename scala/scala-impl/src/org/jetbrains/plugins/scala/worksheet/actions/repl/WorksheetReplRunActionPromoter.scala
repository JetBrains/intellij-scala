package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase

final class WorksheetReplRunActionPromoter extends SingleActionPromoterBase {

  override def shouldPromote(anAction: AnAction): Boolean = 
    anAction.isInstanceOf[WorksheetReplRunAction]
}
