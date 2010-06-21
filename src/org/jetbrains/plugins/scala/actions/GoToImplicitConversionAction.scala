package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.actionSystem.{DataConstants, PlatformDataKeys, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.ui.popup.{LightweightWindowEvent, JBPopupAdapter, JBPopupFactory}
import java.util.ArrayList
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import java.awt.Component
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import javax.swing.{DefaultListModel, DefaultListCellRenderer, JList}
import com.intellij.ide.util.MethodCellRenderer
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitFunctionListCellRenderer
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.{NavigatablePsiElement, PsiNamedElement, PsiElement, PsiFile}

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
    if (!editor.getSelectionModel.hasSelection) return
    val selectionStart = editor.getSelectionModel.getSelectionStart
    val selectionEnd = editor.getSelectionModel.getSelectionEnd
    val opt = ScalaRefactoringUtil.getExpression(project, editor, file, selectionStart, selectionEnd)
    opt match {
      case Some((expr, _)) => {
        val implicitConversions = expr.getImplicitConversions
        val funs = implicitConversions._1
        if (funs.length == 0) return
        var selectedIndex = -1
        val implicitFunction: Option[PsiElement] = implicitConversions._2
        val model: DefaultListModel = new DefaultListModel
        for (element <- funs) {
          model.addElement(element)
        }
        val list: JList = new JList(model)
        list.setCellRenderer(new ScImplicitFunctionListCellRenderer(if (selectedIndex == -1) null else funs(selectedIndex)))

        val builder = JBPopupFactory.getInstance.createListPopupBuilder(list)
        builder.setTitle("Choose implicit conversion method:").
                setMovable(false).setResizable(false).setRequestFocus(true).
                setItemChoosenCallback(new Runnable {
          def run: Unit = {
            val method = list.getSelectedValue.asInstanceOf[PsiNamedElement]
            method match {
              case n: NavigatablePsiElement => n.navigate(true)
              case _ => //do nothing
            }
          }
        }).createPopup.showInBestPositionFor(editor)
      }
      case _ => return
    }
  }
}