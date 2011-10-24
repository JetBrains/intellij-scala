package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.actionSystem.{PlatformDataKeys, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.{DefaultListModel, JList}
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitFunctionListCellRenderer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.{PsiWhiteSpace, PsiElement, NavigatablePsiElement, PsiNamedElement}
import collection.mutable.ArrayBuffer

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

    def forExpr(expr: ScExpression): Boolean = {
      val implicitConversions = expr.getImplicitConversions
      val funs = implicitConversions._1
      if (funs.length == 0) return true
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
      false
    }

    if (editor.getSelectionModel.hasSelection) {
      val selectionStart = editor.getSelectionModel.getSelectionStart
      val selectionEnd = editor.getSelectionModel.getSelectionEnd
      val opt = ScalaRefactoringUtil.getExpression(project, editor, file, selectionStart, selectionEnd)
      opt match {
        case Some((expr, _)) =>
          if (forExpr(expr)) return
        case _ => return
      }
    } else {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
          w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      def getExpressions(guard: Boolean): Array[ScExpression] = {
        val res = new ArrayBuffer[ScExpression]
        var parent = element
        while (parent != null) {
          parent match {
            case expr: ScExpression if guard || expr.getImplicitConversions._2 != None => res += expr
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = {
        val falseGuard = getExpressions(false)
        if (falseGuard.length != 0) falseGuard
        else getExpressions(true)
      }
      def chooseExpression(expr: ScExpression) {
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset,
          expr.getTextRange.getEndOffset)
        forExpr(expr)
      }
      if (expressions.length == 0)
        editor.getSelectionModel.selectLineAtCaret()
      else if (expressions.length == 1) {
        chooseExpression(expressions(0))
      } else {
        ScalaRefactoringUtil.showChooser(editor, expressions, elem =>
          chooseExpression(elem.asInstanceOf[ScExpression]), "Go to implicit function", (expr: ScExpression) => {
          ScalaRefactoringUtil.getShortText(expr)
        })
      }
    }
  }
}