package org.jetbrains.plugins.scala.codeInsight.generation.ui

import javax.swing.event.{TableModelEvent, TableModelListener}

import com.intellij.ide.wizard.{AbstractWizard, Step, StepAdapter}
import com.intellij.openapi.project.Project
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConversions._

/**
 * Wizard dialog to select class members for generation of toString method.
 *
 * @param project IntelliJ project.
 * @param classMembers Class members to choose from.
 *
 * @author Rado Buransky (buransky.com)
 */
class ScalaGenerateToStringWizard(project: Project, classMembers: Seq[ScNamedElement]) extends AbstractWizard[Step](
  ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.title"), project) {

  override def showAndGetOk() = {
    val result = super.showAndGetOk()

    if (this.isOK) {
      val settings = ScalaProjectSettings.getInstance(project)
      settings.setGenerateToStringWithFieldNames(toStringPanel.checkBox.isSelected)
    }

    result
  }

  def getToStringFields: Seq[ScNamedElement] = toStringPanel.getTable.getSelectedMemberInfos.map(_.getMember).toSeq
  def withFieldNames = toStringPanel.checkBox.isSelected

  /**
   * Get IntelliJ help ID.
   * @return IntelliJ help ID.
   */
  override def getHelpID: String = null

  private lazy val toStringPanel = {
    val allFields =  classMembers.map(new ScalaMemberInfo(_))
    val panel = new ScalaToStringMemberSelectionPanel(
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

    val settings = ScalaProjectSettings.getInstance(project)
    toStringPanel.checkBox.setSelected(settings.isGenerateToStringWithFieldNames)
  }

  toStringPanel.getTable.getModel.addTableModelListener(new ToStringTableModelListener)
  addStep(new ToStringStep(toStringPanel))
  init()
  updateButtons()
}

private class ScalaToStringMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]