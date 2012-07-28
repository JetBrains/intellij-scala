package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.{PlatformDataKeys, AnActionEvent, AnAction}
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import javax.swing.{JList, DefaultListModel}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.{NavigatablePsiElement, PsiNamedElement, PsiWhiteSpace, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitParametersListCellRenderer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNewTemplateDefinition, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.editor.colors.EditorFontType

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
          case Some(tp) => named.name //todo:
          case None => named.name //todo:
        }
      //Local value
      case _ => named.name
    }
  }
  
  private def implicitParams(expr: PsiElement): Option[Seq[ScalaResolveResult]] = {
    def checkTypeElement(element: ScTypeElement): Option[Option[scala.Seq[ScalaResolveResult]]] = {
      def checkSimpleType(s: ScSimpleTypeElement) = {
        s.findImplicitParameters
      }
      element match {
        case s: ScSimpleTypeElement =>
          return Some(checkSimpleType(s))
        case p: ScParameterizedTypeElement =>
          p.typeElement match {
            case s: ScSimpleTypeElement =>
              return Some(checkSimpleType(s))
            case _ =>
          }
        case _ =>
      }
      None
    }
    expr match {
      case expr: ScNewTemplateDefinition =>
        expr.extendsBlock.templateParents match {
          case Some(tp) =>
            val elements = tp.typeElements
            if (elements.length > 0) {
              checkTypeElement(elements(0)) match {
                case Some(x) => return x
                case None =>
              }
            }
          case _ =>
        }
      case expr: ScExpression =>
        return expr.findImplicitParameters
      case constr: ScConstructor =>
        checkTypeElement(constr.typeElement) match {
          case Some(x) => return x
          case _ =>
        }
      case _ =>
    }
    None
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val editor = PlatformDataKeys.EDITOR.getData(context)
    if (editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    def forExpr(expr: PsiElement) {
      val implicitParameters = implicitParams(expr)
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
          val renderer = new ScImplicitParametersListCellRenderer
          val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
          renderer.setFont(font)
          list.setFont(font)
          list.setCellRenderer(renderer)

          val builder = JBPopupFactory.getInstance.createListPopupBuilder(list)
          builder.setTitle("Actual implicit parameters:").
            setMovable(false).setResizable(false).setRequestFocus(true).
            setItemChoosenCallback(new Runnable {
            def run() {
              val method = list.getSelectedValue.asInstanceOf[PsiNamedElement]
              method match {
                case f: ScFunction =>
                  f.getSyntheticNavigationElement match {
                    case Some(n: NavigatablePsiElement) => n.navigate(true)
                    case _ => f.navigate(true)
                  }
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
      def getExpressions: Array[PsiElement] = {
        val res = new ArrayBuffer[PsiElement]
        var parent = element
        while (parent != null) {
          implicitParams(parent) match {
            case Some(seq) if seq.length > 0 =>
              parent match {
                case constr: ScConstructor =>
                  var p = constr.getParent
                  if (p != null) p = p.getParent
                  if (p != null) p = p.getParent
                  if (!p.isInstanceOf[ScNewTemplateDefinition]) res += parent
                case _ =>
                  res += parent
              }
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = getExpressions
      def chooseExpression(expr: PsiElement) {
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
          chooseExpression(elem), "Expressions", (expr: PsiElement) => {
          expr match {
            case expr: ScExpression =>
              ScalaRefactoringUtil.getShortText(expr)
            case _ => expr.getText.slice(0, 20)
          }
        })
      }
    }
  }
}