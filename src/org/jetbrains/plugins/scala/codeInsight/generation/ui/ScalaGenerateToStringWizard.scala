package org.jetbrains.plugins.scala.codeInsight.generation.ui

import javax.swing.event.{TableModelEvent, TableModelListener}

import com.intellij.ide.wizard.{AbstractWizard, Step, StepAdapter}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.generation.GenerationUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.JavaConversions._

/**
 * Wizard dialog to select class members for generation of toString method.
 *
 * @param project IntelliJ project.
 * @param aClass Class to generate toString for.
 *
 * @author Rado Buransky (buransky.com)
 */
class ScalaGenerateToStringWizard(project: Project, aClass: PsiClass) extends AbstractWizard[Step](
  ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.title"), project) {

  /**
   * Get selected fields.
   * @return List of fields.
   */
  def getToStringFields: Seq[ScNamedElement] = toStringPanel.getTable.getSelectedMemberInfos.map(_.getMember).toSeq

  /**
   * Get IntelliJ help ID.
   * @return IntelliJ help ID.
   */
  override def getHelpID: String = null

  /**
   * Scala member selection panel.
   */
  private lazy val toStringPanel = {
    val allFields =  GenerationUtil.getAllFields(aClass).map(new ScalaMemberInfo(_))
    val panel = new ScalaMemberSelectionPanel(
      ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.fields"), allFields, null)
    panel.getTable.setMemberInfoModel(new ScalaToStringMemberInfoModel)
    panel
  }

  private class ToStringTableModelListener extends TableModelListener {
    def tableChanged(modelEvent: TableModelEvent) = updateButtons()
  }

  private class ToStringStep(panel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo]) extends StepAdapter {
    override def getComponent = panel
    override def getPreferredFocusedComponent = panel.getTable
  }

  override protected def init(): Unit = {
    super.init()
    updateStep()
  }

  // Constructor initialization
  toStringPanel.getTable.getModel.addTableModelListener(new ToStringTableModelListener)
  addStep(new ToStringStep(toStringPanel))
  init()
  updateButtons()
}

private class ScalaToStringMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]