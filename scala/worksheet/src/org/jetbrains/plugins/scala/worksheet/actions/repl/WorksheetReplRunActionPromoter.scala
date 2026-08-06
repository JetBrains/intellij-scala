package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.openapi.actionSystem.{AnAction, DataContext}
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase

final class WorksheetReplRunActionPromoter extends SingleActionPromoterBase {

  override def shouldPromote(anAction: AnAction, context: DataContext): Boolean =
    anAction.isInstanceOf[WorksheetReplRunAction]
}
