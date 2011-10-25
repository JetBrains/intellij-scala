package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.{PlatformDataKeys, AnActionEvent, AnAction}
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import javax.swing.{JList, DefaultListModel}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.{NavigatablePsiElement, PsiNamedElement, PsiWhiteSpace, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.presentation.{ScImplicitParametersListCellRenderer, ScImplicitFunctionListCellRenderer}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alefas
 * Date: 25.10.11
 */

class ShowImplicitParametersAction extends AnAction("Show implicit parameters action") {
  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  private def presentableText(rr: ScalaResolveResult, context: ScExpression): String = {
    val named = rr.getElement
    ScalaPsiUtil.nameContext(named).getContext match {
      case _: ScTemplateBody | _: ScEarlyDefinitions =>
        rr.fromType match {
          case Some(tp) => named.getName //todo:
          case None => named.getName //todo:
        }
      //Local value
      case _ => named.getName
    }
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val editor = PlatformDataKeys.EDITOR.getData(context)
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    def forExpr(expr: ScExpression) {
      val implicitParameters = expr.findImplicitParameters
      implicitParameters match {
        case None | Some(Seq()) =>
          ScalaActionUtil.showHint(editor, "No implicit parameters")
        case Some(seq) =>
          val defaultElement = ScalaPsiElementFactory.createParameterFromText("NotFoundParameter: Int", expr.getManager)
          val model: DefaultListModel = new DefaultListModel
          for (element <- seq) {
            if (element != null)
              model.addElement(element.getElement)
            else 
              model.addElement(defaultElement)
          }
          val list: JList = new JList(model)
          list.setCellRenderer(new ScImplicitParametersListCellRenderer)

          val builder = JBPopupFactory.getInstance.createListPopupBuilder(list)
          builder.setTitle("Actual implicit parameters:").
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
    }

    if (editor.getSelectionModel.hasSelection) {
      val selectionStart = editor.getSelectionModel.getSelectionStart
      val selectionEnd = editor.getSelectionModel.getSelectionEnd
      val opt = ScalaRefactoringUtil.getExpression(project, editor, file, selectionStart, selectionEnd)
      opt match {
        case Some((expr, _)) =>
          forExpr(expr)
          return
        case _ => return
      }
    } else {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
          w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      def getExpressions: Array[ScExpression] = {
        val res = new ArrayBuffer[ScExpression]
        var parent = element
        while (parent != null) {
          parent match {
            case expr: ScExpression =>
              expr.findImplicitParameters match {
                case Some(seq) if seq.length > 0 => res += expr
                case _ =>
              }
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = getExpressions
      def chooseExpression(expr: ScExpression) {
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset,
          expr.getTextRange.getEndOffset)
        forExpr(expr)
      }
      if (expressions.length == 0) {
        ScalaActionUtil.showHint(editor, "No implicit parameters")
        return
      } else if (expressions.length == 1) {
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