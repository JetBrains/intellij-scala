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

class ScalaGenerateToStringWizard(project: Project, aClass: PsiClass) extends AbstractWizard[Step](
  ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.title"), project) {

  def getToStringFields: Seq[ScNamedElement] = toStringPanel.getTable.getSelectedMemberInfos.map(_.getMember).toSeq
  override def getHelpID: String = "editing.altInsert.toString"

  private lazy val allFields =  GenerationUtil.getAllFields(aClass).map(new ScalaMemberInfo(_))

  private lazy val toStringPanel = {
    val panel = new ScalaMemberSelectionPanel(
      ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.title"), allFields, null)
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

  toStringPanel.getTable.getModel.addTableModelListener(new ToStringTableModelListener)
  addStep(new ToStringStep(toStringPanel))
  init()
  updateButtons()
}

private class ScalaToStringMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]