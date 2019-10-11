package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase

class ReplRunActionPromoter extends SingleActionPromoterBase {

  override def shouldPromote(anAction: AnAction): Boolean = 
    anAction.isInstanceOf[WorksheetReplRunAction]
}
