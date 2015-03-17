package org.jetbrains.plugins.scala
package codeInspection.collections

import java.awt.{Component, GridLayout}
import java.util
import javax.swing._
import javax.swing.event.{ChangeEvent, ChangeListener}

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, ListScrollingUtil, ToolbarDecorator}
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionInspectionBase._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsUtil
import org.jetbrains.plugins.scala.util.JListCompatibility

/**
 * Nikolay.Tropin
 * 5/17/13
 */
object OperationOnCollectionInspectionBase {
  val inspectionId = InspectionBundle.message("operation.on.collection.id")
  val inspectionName = InspectionBundle.message("operation.on.collection.name")

  val likeOptionClassesDefault = Array("scala.Option")
  val likeCollectionClassesDefault = Array("scala.collection._", "scala.Option")

  private val likeOptionKey = "operation.on.collection.like.option"
  private val likeCollectionKey = "operation.on.collection.like.collection"

  private val inputMessages = Map(
    likeCollectionKey -> InspectionBundle.message("operation.on.collection.like.collection.input.message"),
    likeOptionKey -> InspectionBundle.message("operation.on.collection.like.option.input.message")
  )

  private val inputTitles = Map(
    likeCollectionKey -> InspectionBundle.message("operation.on.collection.like.collection.input.title"),
    likeOptionKey -> InspectionBundle.message("operation.on.collection.like.option.input.title")
  )

  private val panelTitles = Map(
    likeCollectionKey -> InspectionBundle.message("operation.on.collection.like.collection.panel.title"),
    likeOptionKey -> InspectionBundle.message("operation.on.collection.like.option.panel.title")
  )
}

abstract class OperationOnCollectionInspectionBase extends AbstractInspection(inspectionId, inspectionName){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression  =>
      for (s <- simplifications(expr)) {
        holder.registerProblem(expr, s.hint, highlightType, s.rangeInParent, new OperationOnCollectionQuickFix(expr, s))
      }
  }

  def highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

  private def simplifications(expr: ScExpression): Array[Simplification] = {
    def simplificationTypes = for {
      (st, idx) <- possibleSimplificationTypes.zipWithIndex
      if getSimplificationTypeChecked(idx)
    } yield st

    val result = expr match {
      case MethodSeq(single) => simplificationTypes.flatMap(_.getSimplification(single))
      case MethodSeq(last, second, _*) =>
        simplificationTypes.flatMap {
          st => st.getSimplification(last, second) ::: st.getSimplification(last)
        }
      case _ => Array[Simplification]()
    }
    result
  }

  def getLikeCollectionClasses: Array[String]
  def getLikeOptionClasses: Array[String]
  def setLikeCollectionClasses(values: Array[String])
  def setLikeOptionClasses(values: Array[String])
  def possibleSimplificationTypes: Array[SimplificationType]
  def getSimplificationTypeChecked: Array[java.lang.Boolean]
  def setSimplificationTypeChecked(values: Array[java.lang.Boolean])

  private val patternLists = Map(
    likeCollectionKey -> getLikeCollectionClasses _,
    likeOptionKey -> getLikeOptionClasses _
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
      for (i <- 0 until possibleSimplificationTypes.length) {
        val checked: Array[java.lang.Boolean] = getSimplificationTypeChecked
        val checkBox = new JCheckBox(possibleSimplificationTypes(i).description, checked(i))
        checkBox.getModel.addChangeListener(new ChangeListener {
          def stateChanged(e: ChangeEvent) {
            checked(i) = checkBox.isSelected
            setSimplificationTypeChecked(checked)
          }
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
      val patternList: Array[String] = patternLists(patternListKey)()
      val listModel = JListCompatibility.createDefaultListModel()
      patternList.foreach(JListCompatibility.add(listModel, listModel.size, _))
      val patternJBList = JListCompatibility.createJBListFromModel(listModel)
      def resetValues() {
        val newArray = listModel.toArray collect {case s: String => s}
        setPatternLists(patternListKey)(newArray)
      }
      val panel = ToolbarDecorator.createDecorator(patternJBList).setAddAction(new AnActionButtonRunnable {
        def addPattern(pattern: String) {
          if (pattern == null) return
          val index: Int = - util.Arrays.binarySearch (listModel.toArray, pattern) - 1
          if (index < 0) return
          JListCompatibility.add(listModel, index, pattern)
          resetValues()
          patternJBList.setSelectedValue (pattern, true)
          ListScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
          IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
        }

        def run(button: AnActionButton) {
          val validator: InputValidator = ScalaProjectSettingsUtil.getPatternValidator
          val inputMessage = inputMessages(patternListKey)
          val inputTitle = inputTitles(patternListKey)
          val newPattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
          addPattern(newPattern)
        }
      }).setRemoveAction(new AnActionButtonRunnable {
        def run(t: AnActionButton) {
          patternJBList.getSelectedIndices.foreach(listModel.removeElementAt)
          resetValues()
        }
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



