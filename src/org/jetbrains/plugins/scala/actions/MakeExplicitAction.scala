package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.actionSystem._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.IntentionUtils
import java.awt.Rectangle

/**
 * @author Ksenia.Sautina
 * @since 6/20/12
 */

object MakeExplicitAction {
  final val MAKE_EXPLICIT = "Make explicit"
  final val MAKE_EXPLICIT_STATICALLY = "Make explicit (Import method)"

}

class MakeExplicitAction  extends AnAction("Replace implicit conversion action") {

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = CommonDataKeys.PROJECT.getData(context)
    val selectedItem = PlatformDataKeys.SELECTED_ITEM.getData(context) match {
      case s: Parameters => s
      case _ => null
    }
    if (selectedItem == null || selectedItem.getNewExpression == null) return
    val function = selectedItem.getNewExpression match {
      case f: ScFunction => f
      case _ => null
    }
    val expression = selectedItem.getOldExpression
    val editor = selectedItem.getEditor
    val secondPart = selectedItem.getSecondPart

    if (project == null || editor == null || secondPart == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    IntentionUtils.showMakeExplicitPopup(project, expression, function, editor, secondPart, getCurrentItemBounds _)
  }

  def getCurrentItemBounds: Rectangle = {
    val index: Int = GoToImplicitConversionAction.getList.getSelectedIndex
    if (index < 0) {
      throw new RuntimeException("Index = " + index + " is less than zero.")
    }
    val itemBounds: Rectangle = GoToImplicitConversionAction.getList.getCellBounds(index, index)
    if (itemBounds == null) {
      throw new RuntimeException("No bounds for index = " + index + ".")
    }
    itemBounds
  }
}