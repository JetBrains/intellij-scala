package org.jetbrains.plugins.scala.actions.implicitConversions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.components.JBList
import com.intellij.util.Alarm
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.{GoToImplicitConversionAction, MakeExplicitAction, Parameters, ScalaActionUtil}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getSelectedExpression
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import java.awt.Color
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing._
import javax.swing.border.Border
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}

final class ShowImplicitConversionsAction extends AnAction(
  ScalaBundle.message("implicit.conversions.action.text"),
  ScalaBundle.message("implicit.conversions.action.description"),
  AllIcons.Actions.IntentionBulb,
) {

  import MakeExplicitAction._

  private var hint: LightBulbHint = _
  private val hintAlarm: Alarm = new Alarm

  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (project == null || editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.is[ScalaFile]) return

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

      val conversions = expr.implicitConversions(fromUnderscore = fromUnderscore)
      if (conversions.isEmpty) return true

      val conversionFun = implicitElement.orNull
      val model = new DefaultListModel[Parameters]
      var actualIndex = -1
      //todo actualIndex should be another if conversionFun is not one

      for (element <- conversions) {
        val elem = Parameters(element, expr, project, editor, conversions)
        model.addElement(elem)
        if (element == conversionFun) {
          actualIndex = model.indexOf(elem)
        }
      }

      val list = new JBList[Parameters](model)
      val renderer = new ScImplicitFunctionListCellRenderer(conversionFun)
      val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
      renderer.setFont(font)
      list.setFont(font)
      list.setCellRenderer(renderer)
      list.getSelectionModel.addListSelectionListener(new ListSelectionListener {
        override def valueChanged(e: ListSelectionEvent): Unit = {
          hintAlarm.cancelAllRequests
          val item = list.getSelectedValue
          if (item == null) return
          updateHint(item)
        }
      })

      createPopup(list).showInBestPositionFor(editor)

      if (actualIndex >= 0 && actualIndex < list.getModel.getSize) {
        list.getSelectionModel.setSelectionInterval(actualIndex, actualIndex)
        list.ensureIndexIsVisible(actualIndex)
      }

      hint = new LightBulbHint(editor, project, expr, conversions)

      false
    }

    Stats.trigger(FeatureKey.goToImplicitConversion)

    if (editor.getSelectionModel.hasSelection) {
      getSelectedExpression(file).foreach(forExpr)
    } else {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
          w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      def getExpressions(guard: Boolean): Seq[ScExpression] = {
        val res = Seq.newBuilder[ScExpression]
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
        res.result()
      }
      val expressions = {
        val falseGuard = getExpressions(guard = false)
        if (falseGuard.nonEmpty) falseGuard
        else getExpressions(guard = true)
      }
      def chooseExpression(expr: ScExpression): Unit = {
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset,
          expr.getTextRange.getEndOffset)
        forExpr(expr)
      }

      expressions match {
        case Seq() => editor.getSelectionModel.selectLineAtCaret()
        case Seq(expression) => chooseExpression(expression)
        case expressions =>
          ScalaRefactoringUtil.showChooser(editor, expressions, (elem: ScExpression)=>
            chooseExpression(elem), ScalaBundle.message("title.expressions"), (expr: ScExpression) => {
            ScalaRefactoringUtil.getShortText(expr)
          })
      }
    }
  }

  private def updateHint(element: Parameters): Unit = {
    if (element.newExpression == null || !element.newExpression.isValid) return
    val list = GoToImplicitConversionAction.getList

    if (hint != null) {
      list.remove(hint)
      hint = null

      list.revalidate()
      list.repaint()
    }

    hintAlarm.addRequest(new Runnable {
      override def run(): Unit = {
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

    private val toolTipText: String = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))

    if (toolTipText.nonEmpty) {
      setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", toolTipText))
    }

    addMouseListener(new MouseAdapter {
      override def mouseEntered(e: MouseEvent): Unit =
        setBorder(ACTIVE_BORDER)

      override def mouseExited(e: MouseEvent): Unit =
        setBorder(INACTIVE_BORDER)

      override def mousePressed(e: MouseEvent): Unit = e.getButton match {
        case MouseEvent.BUTTON1 if !e.isPopupTrigger =>
          GoToImplicitConversionAction.getList.getSelectedValue match {
            case Parameters(function: ScFunction, _, _, _, _) => showMakeExplicitPopup(expr, function, elements)(project, editor)
            case _ =>
          }
        case _ =>
      }
    })

    def setBulbLayout(): Unit = {
      val list = GoToImplicitConversionAction.getList
      list.getSelectedValue match {
        case Parameters(newExpression, _, _, _, _) if newExpression != null =>
          setSize(getPreferredSize)
          setLocation(currentItemPoint(list, getWidth + INDENT))
        case _ =>
      }
    }
  }
}
