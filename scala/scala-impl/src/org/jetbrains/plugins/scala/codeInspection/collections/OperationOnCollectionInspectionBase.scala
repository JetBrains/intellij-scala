package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.ui._
import com.intellij.ui.components.JBList
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionInspectionBase._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettingsUtil}

import java.awt.{Component, GridLayout}
import java.util
import javax.swing._
import javax.swing.event.ChangeEvent
import scala.annotation.nowarn
import scala.collection.immutable.ArraySeq

object OperationOnCollectionInspectionBase {
  val inspectionId: String = ScalaInspectionBundle.message("operation.on.collection.id")
  val inspectionName: String = ScalaInspectionBundle.message("operation.on.collection.name")

  val likeOptionClassesDefault: Array[String] = Array("scala.Option", "scala.Some", "scala.None")
  val likeCollectionClassesDefault: Array[String] = Array("scala.collection._", "scala.Array", "scala.Option", "scala.Some", "scala.None", "java.lang.String")

  private val likeOptionKey = "operation.on.collection.like.option"
  private val likeCollectionKey = "operation.on.collection.like.collection"

  private val inputMessages = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.input.message"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.input.message")
  )

  private val inputTitles = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.input.title"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.input.title")
  )

  private val panelTitles = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.panel.title"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.panel.title")
  )

  object SimplifiableExpression {
    def unapply(expr: ScExpression): Option[ScExpression] =
      if (expr.is[ScBlock, ScParenthesisedExpr]) None
      else Some(expr)
  }
}

@nowarn("msg=" + AbstractInspection.DeprecationText)
abstract class OperationOnCollectionInspectionBase extends AbstractInspection(inspectionName) {
  private val settings = ScalaApplicationSettings.getInstance()

  override protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case SimplifiableExpression(expr) => simplifications(expr).foreach {
      case s@Simplification(toReplace, _, hint, rangeInParent) =>
        val quickFix = OperationOnCollectionQuickFix(s)
        holder.registerProblem(toReplace.getElement, hint, highlightType, rangeInParent, quickFix)
    }
  }

  def highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

  private def simplifications(expr: ScExpression): Seq[Simplification] = {
    def simplificationTypes = for {
      (st, idx) <- possibleSimplificationTypes.zipWithIndex
      if getSimplificationTypesEnabled(idx)
    } yield st

    simplificationTypes.flatMap(st => st.getSimplifications(expr) ++ st.getSimplification(expr))
  }

  def getLikeCollectionClasses: Seq[String] = ArraySeq.unsafeWrapArray(settings.getLikeCollectionClasses)
  def getLikeOptionClasses: Seq[String] = ArraySeq.unsafeWrapArray(settings.getLikeOptionClasses)
  def setLikeCollectionClasses(values: Seq[String]): Unit = settings.setLikeCollectionClasses(values.toArray)
  def setLikeOptionClasses(values: Seq[String]): Unit = settings.setLikeOptionClasses(values.toArray)

  def possibleSimplificationTypes: Seq[SimplificationType]
  def getSimplificationTypesEnabled: Array[java.lang.Boolean]
  def setSimplificationTypesEnabled(values: Array[java.lang.Boolean]): Unit

  private val patternLists = Map(
    likeCollectionKey -> (() => getLikeCollectionClasses),
    likeOptionKey -> (() => getLikeOptionClasses)
  )

  private val setPatternLists = {
    Map(
      likeCollectionKey -> setLikeCollectionClasses _,
      likeOptionKey -> setLikeOptionClasses _
    )
  }

  override def createOptionsPanel: JComponent = {
    def checkBoxesPanel(): JComponent = {
      val innerPanel = new JPanel()
      innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS))
      for (i <- possibleSimplificationTypes.indices) {
        val enabled = getSimplificationTypesEnabled
        val checkBox = new JCheckBox(possibleSimplificationTypes(i).description, enabled(i))
        checkBox.getModel.addChangeListener((_: ChangeEvent) => {
          setSimplificationTypesEnabled(getSimplificationTypesEnabled.updated(i, checkBox.isSelected))
        })
        innerPanel.add(checkBox)
      }
      val extPanel = new JPanel()
      extPanel.setLayout(new BoxLayout(extPanel, BoxLayout.X_AXIS))
      extPanel.add(innerPanel)
      extPanel.add(Box.createHorizontalGlue())
      extPanel
    }

    def createPatternListPanel(parent: JComponent, patternListKey: String): JComponent = {
      val patternList = patternLists(patternListKey)()
      val listModel = new DefaultListModel[String]()
      patternList.foreach(listModel.add(listModel.size, _))
      val patternJBList = new JBList[String](listModel)
      def resetValues(): Unit = {
        val newArray = listModel.toArray collect {case s: String => s}
        setPatternLists(patternListKey)(ArraySeq.unsafeWrapArray(newArray))
      }
      val panel = ToolbarDecorator.createDecorator(patternJBList).setAddAction(new AnActionButtonRunnable {
        def addPattern(pattern: String): Unit = {
          if (pattern == null) return
          val index: Int = - util.Arrays.binarySearch (listModel.toArray, pattern) - 1
          if (index < 0) return
          listModel.add(index, pattern)
          resetValues()
          patternJBList.setSelectedValue (pattern, true)
          ScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
          IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
        }

        override def run(button: AnActionButton): Unit = {
          val validator: InputValidator = ScalaProjectSettingsUtil.getPatternValidator
          val inputMessage = inputMessages(patternListKey)
          val inputTitle = inputTitles(patternListKey)
          val newPattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
          addPattern(newPattern)
        }
      }).setRemoveAction((_: AnActionButton) => {
        patternJBList.getSelectedIndices.foreach(listModel.removeElementAt)
        resetValues()
      }).disableUpDownActions.createPanel

      val title = panelTitles(patternListKey)
      val border = BorderFactory.createTitledBorder(title)
      panel.setBorder(border)
      panel
    }

    def patternsPanel(): JComponent = {

      val panel = new JPanel(new GridLayout(1,2))
      val likeCollectionPanel = createPatternListPanel(panel, likeCollectionKey)
      val likeOptionPanel = createPatternListPanel(panel, likeOptionKey)
      panel.add(likeCollectionPanel)
      panel.add(likeOptionPanel)
      panel
    }

    val panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    if (possibleSimplificationTypes.length > 1) {
      val chbPanel = checkBoxesPanel()
      chbPanel.setAlignmentX(Component.LEFT_ALIGNMENT)
      panel.add(checkBoxesPanel())
    }
    panel.add(Box.createVerticalGlue())
    panel.add(patternsPanel())
    panel
  }
}



