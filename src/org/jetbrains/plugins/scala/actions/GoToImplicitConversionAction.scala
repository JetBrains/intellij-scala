package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.actionSystem.{PlatformDataKeys, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.{DefaultListModel, JList}
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitFunctionListCellRenderer
import com.intellij.psi.{NavigatablePsiElement, PsiNamedElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2010
 */

class GoToImplicitConversionAction extends AnAction("Go to implicit conversion action") {
  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val editor = PlatformDataKeys.EDITOR.getData(context)
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return
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
        val conversionFun = implicitConversions._2
        conversionFun match {
          case Some(fun) => selectedIndex = funs.findIndexOf(_ == fun)
          case _ =>
        }
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
          def run() {
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