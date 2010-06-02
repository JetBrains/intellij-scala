package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.actionSystem.{DataConstants, PlatformDataKeys, AnActionEvent, AnAction}

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2010
 */

class GoToImplicitConversionAction extends AnAction("Go to implicit conversion action") {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    def enable {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val dataContext = e.getDataContext
      val file = dataContext.getData(DataConstants.PSI_FILE)
      file match {
        case _: ScalaFile => enable
        case _ => disable
      }
    }
    catch {
      case e: Exception => disable
    }
  }

  def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val editor = PlatformDataKeys.EDITOR.getData(context)
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return
    val scalaFile = file.asInstanceOf[ScalaFile]
    val offset = editor.getCaretModel.getOffset
  }

  private def collectImplicitExpr(file: ScalaFile, offset: Int): Seq[(ScExpression, ScFunctionDefinition)] = {
    Seq.empty
  }
}