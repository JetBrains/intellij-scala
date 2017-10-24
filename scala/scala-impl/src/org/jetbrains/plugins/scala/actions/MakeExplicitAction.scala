package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.util.IntentionUtils.showMakeExplicitPopup

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
    if (selectedItem == null || selectedItem.newExpression == null) return
    val function = selectedItem.newExpression match {
      case f: ScFunction => f
      case _ => null
    }
    val expression = selectedItem.oldExpression
    val editor = selectedItem.editor
    val elements = selectedItem.elements

    if (project == null || editor == null || elements == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    showMakeExplicitPopup(project, expression, function, editor, elements)
  }
}