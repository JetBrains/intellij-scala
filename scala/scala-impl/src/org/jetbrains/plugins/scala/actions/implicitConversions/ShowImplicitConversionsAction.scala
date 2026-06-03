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
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.utils.TaskRunnerWithLoadingProgress
import org.jetbrains.plugins.scala.actions.{GoToImplicitConversionAction, MakeExplicitAction, Parameters, ScalaActionUtil}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getSelectedExpression
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing._
import javax.swing.border.Border
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import kotlinx.coroutines.CoroutineScope

final class ShowImplicitConversionsAction(cs: CoroutineScope) extends AnAction(
  ScalaBundle.message("implicit.conversions.action.text"),
  ScalaBundle.message("implicit.conversions.action.description"),
  AllIcons.Actions.IntentionBulb,
) {

  import MakeExplicitAction._

  private var hint: LightBulbHint = _
  private val hintAlarm: Alarm = new Alarm(cs, Alarm.ThreadToUse.SWING_THREAD)

  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (project == null || editor == null)
      return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (file == null || !file.is[ScalaFile])
      return

    ScalaActionUsagesCollector.logGoToImplicitConversion(file.getProject)

    TaskRunnerWithLoadingProgress.runSingleInstanceActionTask[Seq[ScExpression]](
      project = project,
      backgroundDataSupplier = () => {
        findTargetExpressions(editor, file, project)
      },
      uiDataConsumer = {
        case Seq() =>
          editor.getSelectionModel.selectLineAtCaret()
        case Seq(expression) =>
          selectExpressionAndFindAndShowConversions(expression, editor, project)
        case expressions =>
          ScalaRefactoringUtil.showPsiChooser(
            expressions,
            (elem: ScExpression) => selectExpressionAndFindAndShowConversions(elem, editor, project),
            ScalaBundle.message("title.expressions"),
            (expr: ScExpression) => ScalaRefactoringUtil.getShortText(expr)
          )
      },
      progressTitle = ScalaBundle.message("searching.for.action.target.expressions"),
      editor = editor,
      // I decided not to cancel the tooltip on scrolling - if it takes long to compute the types in complex code bases,
      // it can be annoying that you can't even scroll the file...
      cancelOnScrolling = false,
      originalAction = ShowImplicitConversionsAction.this
    )
  }

  private def selectExpressionAndFindAndShowConversions(
    expr: ScExpression,
    editor: Editor,
    project: Project,
  ): Unit = {
    val range = expr.getTextRange
    editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)

    TaskRunnerWithLoadingProgress.runSingleInstanceActionTask[(Option[PsiNamedElement], Seq[PsiNamedElement])](
      project = project,
      backgroundDataSupplier = () => {
        calculateConversionsData(expr)
      },
      uiDataConsumer = {
        case (implicitElement, conversions) =>
          if (conversions.nonEmpty) {
            showConversionsPopup(expr, implicitElement.orNull, conversions, editor, project)
          }
      },
      progressTitle = ScalaBundle.message("searching.for.implicit.conversions"),
      editor = editor,
      // I decided not to cancel the tooltip on scrolling - if it takes long to compute the types in complex code bases,
      // it can be annoying that you can't even scroll the file...
      cancelOnScrolling = false,
      originalAction = ShowImplicitConversionsAction.this
    )
  }

  @RequiresBackgroundThread // Can involve heavy resolution in complex code bases
  private def findTargetExpressions(editor: Editor, file: PsiFile, project: Project): Seq[ScExpression] = {
    implicit val p: Project = project
    implicit val e: Editor = editor

    if (editor.getSelectionModel.hasSelection) {
      getSelectedExpression(file).toSeq
    } else {
      val offset = editor.getCaretModel.getOffset
      //Q: what is this for? Isn't it trying to do what `com.intellij.codeInsight.TargetElementUtil.adjustOffset` is designed for?
      // (adjustOffset is used in  used in org.jetbrains.plugins.scala.actions.ShowTypeInfoAction)
      val elementAtCaretOriginal = file.findElementAt(offset)
      val elementAtCaret = elementAtCaretOriginal match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset && w.getText.contains("\n") =>
          file.findElementAt(offset - 1)
        case p => p
      }
      if (elementAtCaret == null)
        return Nil

      val expressionsWithoutGuard = getExpressions(elementAtCaret, guard = false)
      if (expressionsWithoutGuard.nonEmpty)
        expressionsWithoutGuard
      else
        getExpressions(elementAtCaret, guard = true)
    }
  }

  @RequiresBackgroundThread // Can involve heavy resolution in complex code bases
  private def getExpressions(element: PsiElement, guard: Boolean): Seq[ScExpression] = {
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

  @RequiresBackgroundThread // Can involve heavy resolution in complex code bases
  private def calculateConversionsData(expr: ScExpression): (Option[PsiNamedElement], Seq[PsiNamedElement]) = {
    val (implicitElement: Option[PsiNamedElement], fromUnderscore: Boolean) = {
      def additionalImplicitElement: Option[PsiNamedElement] = expr.getAdditionalExpression.flatMap {
        case (additional, tp) => additional.implicitElement(expectedOption = Some(tp))
      }

      if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) {
        expr.implicitElement(fromUnderscore = true) match {
          case someElement@Some(_) =>
            (someElement, true)
          case _ =>
            (expr.implicitElement().orElse(additionalImplicitElement), false)
        }
      } else {
        (additionalImplicitElement.orElse(expr.implicitElement()), false)
      }
    }

    val conversions = expr.implicitConversions(fromUnderscore = fromUnderscore)
    (implicitElement, conversions)
  }

  @RequiresEdt
  private def showConversionsPopup(
    expr: ScExpression,
    @Nullable implicitElement: PsiNamedElement,
    conversions: Seq[PsiNamedElement],
    editor: Editor,
    project: Project,
  ): Unit = {
    val listModel = new DefaultListModel[Parameters]

    //todo actualIndex should be another if conversionFun is not one
    var actualIndex = -1
    for (element <- conversions) {
      val elem = Parameters(element, expr, project, editor, conversions)
      listModel.addElement(elem)

      if (element == implicitElement) {
        actualIndex = listModel.indexOf(elem)
      }
    }

    val list = new JBList[Parameters](listModel)
    val renderer = new ScImplicitFunctionListCellRenderer(implicitElement, expr)

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

    // NOTE: this creates some global static popup =/
    val popup = MakeExplicitAction.createPopup(list)
    popup.showInBestPositionFor(editor)

    if (actualIndex >= 0 && actualIndex < list.getModel.getSize) {
      list.getSelectionModel.setSelectionInterval(actualIndex, actualIndex)
      list.ensureIndexIsVisible(actualIndex)
    }

    hint = new LightBulbHint(editor, project, expr, conversions)
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

  private class LightBulbHint(editor: Editor, project: Project, expr: ScExpression, elements: Seq[PsiNamedElement]) extends JLabel {
    private final val INACTIVE_BORDER: Border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    private final val ACTIVE_BORDER: Border =
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JBColor.BLACK, 1),
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
