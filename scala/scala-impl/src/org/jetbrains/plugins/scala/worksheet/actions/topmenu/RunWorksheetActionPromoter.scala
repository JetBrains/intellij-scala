package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.openapi.actionSystem.{AnAction, CommonDataKeys, DataContext}
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class RunWorksheetActionPromoter extends SingleActionPromoterBase {

  override def shouldPromote(anAction: AnAction, context: DataContext): Boolean = {
    anAction.isInstanceOf[RunWorksheetAction] &&
      context.getData(CommonDataKeys.PSI_FILE).isInstanceOf[ScalaFile]
  }
}