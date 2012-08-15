package org.jetbrains.plugins.scala.actions

import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.actionSystem._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory}
import javax.swing._
import org.jetbrains.plugins.scala.lang.psi.presentation.ScImplicitFunctionListCellRenderer
import com.intellij.psi._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.codeInsight.CodeInsightBundle
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.border.Border
import java.awt.{Font, Point, Rectangle, Color}
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import com.intellij.util.Alarm
import scala.Some
import org.jetbrains.plugins.scala.util.IntentionUtils
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.colors.EditorFontType

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2010
 */

object GoToImplicitConversionAction {
  var popup: JBPopup = null
  var list: JList = null

  def getPopup = popup

  def setPopup(p: JBPopup) {
    popup = p
  }

  def getList = list

  def setList(l: JList) {
    list = l
  }
}

class GoToImplicitConversionAction extends AnAction("Go to implicit conversion action") {
  private var hint: LightBulbHint = null
  private val hintAlarm: Alarm = new Alarm

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val editor = PlatformDataKeys.EDITOR.getData(context)
    if (project == null || editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    def forExpr(expr: ScExpression): Boolean = {
      val implicitConversions = { //todo: too complex logic, should be simplified, and moved into one place
        lazy val additionalExpression = expr.getAdditionalExpression
        if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) {
          val conv1 = expr.getImplicitConversions(fromUnder = false)
          val conv2 = expr.getImplicitConversions(fromUnder = true)
          if (conv2._2 != None) conv2
          else if (conv1._2 != None) conv1
          else if (additionalExpression != None) {
            val conv3 = additionalExpression.get._1.getImplicitConversions(fromUnder = false, expectedOption = Some(additionalExpression.get._2))
            if (conv3._2 != None) conv3
            else conv1
          } else conv1
        } else if (additionalExpression != None) {
          val conv3 = additionalExpression.get._1.getImplicitConversions(fromUnder = false, expectedOption = Some(additionalExpression.get._2))
          if (conv3._2 != None) conv3
          else expr.getImplicitConversions(fromUnder = false)
        } else expr.getImplicitConversions(fromUnder = false)
      }
      val functions = implicitConversions._1
      if (functions.length == 0) return true
      val conversionFun = implicitConversions._2.getOrElse(null)
      val model: DefaultListModel = new DefaultListModel
      val firstPart = implicitConversions._3.sortBy(_.getName)
      val secondPart = implicitConversions._4.sortBy(_.getName)
      var actualIndex = -1
      //todo actualIndex should be another if conversionFun is not one
      for (element <- firstPart) {
        val elem = new Parameters(element, expr, editor, firstPart, secondPart)
        model.addElement(elem)
        if (element == conversionFun) actualIndex = model.indexOf(elem)
      }
      for (element <- secondPart) {
        val elem = new Parameters(element, expr, editor, firstPart, secondPart)
        model.addElement(elem)
        if (element == conversionFun) actualIndex = model.indexOf(elem)
      }
      val list: JList = new JList(model)
      val renderer = new ScImplicitFunctionListCellRenderer(conversionFun)
      val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
      renderer.setFont(font)
      list.setFont(font)
      list.setCellRenderer(renderer)
      list.getSelectionModel.addListSelectionListener(new ListSelectionListener {
        def valueChanged(e: ListSelectionEvent) {
          hintAlarm.cancelAllRequests
          val item = list.getSelectedValue.asInstanceOf[Parameters]
          if (item == null) return
          updateHint(item, project)
        }
      })
      GoToImplicitConversionAction.setList(list)

      val builder = JBPopupFactory.getInstance.createListPopupBuilder(list)
      val popup = builder.setTitle("Choose implicit conversion method:").setAdText("Press Alt+Enter").
      setMovable(false).setResizable(false).setRequestFocus(true).
      setItemChoosenCallback(new Runnable {
        def run() {
          val entity = list.getSelectedValue.asInstanceOf[Parameters]
          entity.getNewExpression match {
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

      GoToImplicitConversionAction.setPopup(popup)

      hint = new LightBulbHint(editor, project, expr)
      hint.createHint(firstPart, secondPart)

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
            case expr: ScReferenceExpression if guard => {
              expr.getContext match {
                case postf: ScPostfixExpr if postf.operation == expr =>
                case pref: ScPrefixExpr if pref.operation == expr =>
                case inf: ScInfixExpr if inf.operation == expr =>
                case _ => res += expr
              }
            }
            case expr: ScExpression if guard || expr.getImplicitConversions(fromUnder = false)._2 != None ||
              (ScUnderScoreSectionUtil.isUnderscoreFunction(expr) &&
                expr.getImplicitConversions(fromUnder = true)._2 != None) || (expr.getAdditionalExpression != None &&
                expr.getAdditionalExpression.get._1.getImplicitConversions(fromUnder = false,
                expectedOption = Some(expr.getAdditionalExpression.get._2))._2 != None) => res += expr
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
        ScalaRefactoringUtil.showChooser(editor, expressions, elem =>
          chooseExpression(elem.asInstanceOf[ScExpression]), "Expressions", (expr: ScExpression) => {
          ScalaRefactoringUtil.getShortText(expr)
        })
      }
    }
  }

  private def updateHint(element: Parameters, project: Project) {
    if (element.getNewExpression == null || !element.getNewExpression.isValid) return
    if (hint != null) {
      GoToImplicitConversionAction.getList.remove(hint)
      hint = null

      GoToImplicitConversionAction.getList.revalidate()
      GoToImplicitConversionAction.getList.repaint()
    }

    hintAlarm.addRequest(new Runnable {
      def run() {
        hint = new LightBulbHint(element.getEditor, project, element.getOldExpression)
        hint.createHint(element.getFirstPart, element.getSecondPart)
        GoToImplicitConversionAction.getList.add(hint, 20, 0)
        hint.setBulbLayout()
      }
    }, 500)
  }

  class LightBulbHint(editor: Editor, project: Project, expr: ScExpression) extends JLabel {
    private final val INACTIVE_BORDER: Border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    private final val ACTIVE_BORDER: Border =
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1),
      BorderFactory.createEmptyBorder(3, 3, 3, 3))
    private final val INDENT = 20

    def createHint(firstPart: Seq[PsiNamedElement], secondPart: Seq[PsiNamedElement]) {
      setOpaque(false)
      setBorder(INACTIVE_BORDER)
      setIcon(IconLoader.findIcon("/actions/intentionBulb.png"))

      val toolTipText: String = KeymapUtil.getFirstKeyboardShortcutText(
        ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))

      if (toolTipText.length > 0) {
        setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", toolTipText))
      }

      addMouseListener(new MouseAdapter {
        override def mouseEntered(e: MouseEvent) {
          setBorder(ACTIVE_BORDER)
        }

        override def mouseExited(e: MouseEvent) {
          setBorder(INACTIVE_BORDER)
        }

        override def mousePressed(e: MouseEvent) {
          if (!e.isPopupTrigger && e.getButton == MouseEvent.BUTTON1) {
            val tuple = GoToImplicitConversionAction.getList.getSelectedValue.asInstanceOf[Parameters]
            val function: ScFunction =
              if (tuple.getNewExpression.isInstanceOf[ScFunction]) tuple.getNewExpression.asInstanceOf[ScFunction]
              else null
            if (function == null) return

            IntentionUtils.showMakeExplicitPopup(project, expr, function, editor, secondPart, getCurrentItemBounds _)
          }
        }
      })
    }

    def setBulbLayout() {
      if (this != null && getCurrentItem != null) {
        val bounds: Rectangle = getCurrentItemBounds
        setSize(getPreferredSize)
        setLocation(new Point(bounds.x + bounds.width - getWidth - INDENT, bounds.y))
      }
    }

    def getCurrentItem: PsiNamedElement = GoToImplicitConversionAction.getList.getSelectedValue.asInstanceOf[Parameters].getNewExpression

    def getCurrentItemBounds: Rectangle = {
      val index: Int = GoToImplicitConversionAction.getList.getSelectedIndex
      if (index < 0) {
        throw new RuntimeException("Index = " + index + " is less than zero.")
      }
      val itemBounds: Rectangle = GoToImplicitConversionAction.getList.getCellBounds(index, index)
      if (itemBounds == null) {
        throw new RuntimeException("No bounds for index = " + index + ".")
        return null
      }
      itemBounds
    }
  }
}