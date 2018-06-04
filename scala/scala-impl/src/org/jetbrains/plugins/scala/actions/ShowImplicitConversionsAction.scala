package org.jetbrains.plugins.scala.actions

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Color, Point}
import javax.swing._
import javax.swing.border.Border
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}

import scala.collection.mutable.ArrayBuffer

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory}
import com.intellij.openapi.util.IconLoader
import com.intellij.psi._
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.Alarm
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitFunctionListCellRenderer
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getExpression
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.util.IntentionUtils.showMakeExplicitPopup
import org.jetbrains.plugins.scala.util.{IntentionUtils, JListCompatibility}

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2010
 */

object ShowImplicitConversionsAction {
  var popup: JBPopup = null

  def getPopup: JBPopup = popup

  def setPopup(p: JBPopup) {
    popup = p
  }
}

class ShowImplicitConversionsAction extends AnAction("Show implicit conversions") {
  private var hint: LightBulbHint = null
  private val hintAlarm: Alarm = new Alarm

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (project == null || editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    def forExpr(expr: ScExpression): Boolean = {
      val (implicitElement: Option[PsiNamedElement], fromUnderscore: Boolean) = {
        def additionalImplicitElement = expr.getAdditionalExpression.flatMap {
          case (additional, tp) => additional.implicitElement(expectedOption = Some(tp))
        }

        if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) {
          expr.implicitElement(fromUnderscore = true) match {
            case someElement@Some(_) => (someElement, true)
            case _ => (expr.implicitElement().orElse(additionalImplicitElement), false)
          }
        } else (additionalImplicitElement.orElse(expr.implicitElement()), false)
      }

      val conversions = expr.getAllImplicitConversions(fromUnderscore = fromUnderscore)
      if (conversions.isEmpty) return true

      val conversionFun = implicitElement.orNull
      val model = JListCompatibility.createDefaultListModel()
      var actualIndex = -1
      //todo actualIndex should be another if conversionFun is not one

      for (element <- conversions) {
        val elem = Parameters(element, expr, project, editor, conversions)
        JListCompatibility.addElement(model, elem)
        if (element == conversionFun) actualIndex = model.indexOf(elem)
      }

      val list = JListCompatibility.createJListFromModel(model)
      val renderer = new ScImplicitFunctionListCellRenderer(conversionFun)
      val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
      renderer.setFont(font)
      list.setFont(font)
      JListCompatibility.setCellRenderer(list, renderer)
      list.getSelectionModel.addListSelectionListener(new ListSelectionListener {
        def valueChanged(e: ListSelectionEvent) {
          hintAlarm.cancelAllRequests
          val item = list.getSelectedValue.asInstanceOf[Parameters]
          if (item == null) return
          updateHint(item)
        }
      })
      JListCompatibility.GoToImplicitConversionAction.setList(list)

      val builder = JBPopupFactory.getInstance.createListPopupBuilder(list)
      val popup = builder.setTitle("Choose implicit conversion method:").setAdText("Press Alt+Enter").
      setMovable(false).setResizable(false).setRequestFocus(true).
      setItemChoosenCallback(new Runnable {
        def run() {
          val entity = list.getSelectedValue.asInstanceOf[Parameters]
          entity.newExpression match {
            case f: ScFunction =>
              f.getSyntheticNavigationElement match {
                case Some(n: NavigatablePsiElement) => n.navigate(true)
                case _ => f.navigate(true)
              }
            case n: NavigatablePsiElement => n.navigate(true)
            case _ => //do nothing
          }
        }
      }).createPopup
      popup.showInBestPositionFor(editor)

      if (actualIndex >= 0 && actualIndex < list.getModel.getSize) {
        list.getSelectionModel.setSelectionInterval(actualIndex, actualIndex)
        list.ensureIndexIsVisible(actualIndex)
      }

      ShowImplicitConversionsAction.setPopup(popup)

      hint = new LightBulbHint(editor, project, expr, conversions)

      false
    }

    Stats.trigger(FeatureKey.goToImplicitConversion)

    if (editor.getSelectionModel.hasSelection) {
      getExpression(file).foreach(forExpr)
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
            case expr: ScReferenceExpression if guard =>
              expr.getContext match {
                case postf: ScPostfixExpr if postf.operation == expr =>
                case pref: ScPrefixExpr if pref.operation == expr =>
                case inf: ScInfixExpr if inf.operation == expr =>
                case _ => res += expr
              }
            case expr: ScExpression if guard || expr.implicitElement().isDefined ||
              (ScUnderScoreSectionUtil.isUnderscoreFunction(expr) &&
                expr.implicitElement(fromUnderscore = true).isDefined) || expr.getAdditionalExpression.flatMap {
              case (additional, tp) => additional.implicitElement(expectedOption = Some(tp))
            }.isDefined =>
              res += expr
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = {
        val falseGuard = getExpressions(guard = false)
        if (falseGuard.length != 0) falseGuard
        else getExpressions(guard = true)
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
        ScalaRefactoringUtil.showChooser(editor, expressions, (elem: ScExpression)=>
          chooseExpression(elem), "Expressions", (expr: ScExpression) => {
          ScalaRefactoringUtil.getShortText(expr)
        })
      }
    }
  }

  private def updateHint(element: Parameters): Unit = {
    if (element.newExpression == null || !element.newExpression.isValid) return
    val list = JListCompatibility.GoToImplicitConversionAction.getList

    if (hint != null) {
      list.remove(hint)
      hint = null

      list.revalidate()
      list.repaint()
    }

    hintAlarm.addRequest(new Runnable {
      def run() {
        hint = new LightBulbHint(element.editor, element.project, element.oldExpression, element.elements)
        list.add(hint, 20, 0)
        hint.setBulbLayout()
      }
    }, 500)
  }

  class LightBulbHint(editor: Editor, project: Project, expr: ScExpression, elements: Seq[PsiNamedElement]) extends JLabel {
    private final val INACTIVE_BORDER: Border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    private final val ACTIVE_BORDER: Border =
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1),
        BorderFactory.createEmptyBorder(3, 3, 3, 3))
    private final val INDENT = 20

    setOpaque(false)
    setBorder(INACTIVE_BORDER)
    setIcon(IconLoader.findIcon("/actions/intentionBulb.png"))

    private val toolTipText: String = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))

    if (toolTipText.length > 0) {
      setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", toolTipText))
    }

    addMouseListener(new MouseAdapter {
      override def mouseEntered(e: MouseEvent): Unit = {
        setBorder(ACTIVE_BORDER)
      }

      override def mouseExited(e: MouseEvent): Unit = {
        setBorder(INACTIVE_BORDER)
      }

      override def mousePressed(e: MouseEvent): Unit = {
        if (!e.isPopupTrigger && e.getButton == MouseEvent.BUTTON1) {
          selectedValue.newExpression match {
            case function: ScFunction =>
              showMakeExplicitPopup(project, expr, function, editor, elements)
            case _ =>
          }
        }
      }
    })

    def setBulbLayout(): Unit = {
      if (selectedValue.newExpression != null) {
        val bounds = IntentionUtils.getCurrentItemBounds
        setSize(getPreferredSize)
        setLocation(new Point(bounds.x + bounds.width - getWidth - INDENT, bounds.y))
      }
    }

    private def selectedValue =
      JListCompatibility.GoToImplicitConversionAction.getList
        .getSelectedValue.asInstanceOf[Parameters]
  }

}