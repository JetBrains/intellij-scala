package org.jetbrains.plugins.scala
package codeInsight
package generation
package ui

import java.awt.BorderLayout

import com.intellij.ide.wizard.{AbstractWizard, Step, StepAdapter}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.AsyncResult
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.ui.{AbstractMemberSelectionPanel, AbstractMemberSelectionTable}
import javax.swing.JCheckBox
import javax.swing.event.{TableModelEvent, TableModelListener}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
  * Wizard dialog to select class members for generation of toString method.
  *
  * @param project      IntelliJ project.
  * @param classMembers Class members to choose from.
  */
final class ScalaGenerateToStringWizard(classMembers: Seq[ScNamedElement])
                                       (implicit project: Project)
  extends AbstractWizard[Step](
    ScalaCodeInsightBundle.message("generate.ui.toString.title"),
    project
  ) {

  import ScalaGenerateToStringWizard._

  private val toStringPanel = new Panel(classMembers)((_: TableModelEvent) => updateButtons())

  def membersWithSelection: (Seq[ScNamedElement], Boolean) =
    (toStringPanel.members.toSeq, toStringPanel.isSelected)

  override def showAndGetOk(): AsyncResult[java.lang.Boolean] @nowarn("cat=deprecation") = {
    @nowarn("cat=deprecation") val result = super.showAndGetOk()

    if (isOK) settings.setGenerateToStringWithPropertiesNames(toStringPanel.isSelected)

    result
  }

  /**
    * Get IntelliJ help ID.
    *
    * @return IntelliJ help ID.
    */
  override def getHelpID: String = null

  override protected def init(): Unit = {
    super.init()
    updateStep()

    toStringPanel.isSelected = settings.isGenerateToStringWithPropertiesNames
  }

  addStep(new ToStringStep(toStringPanel))
  init()
  updateButtons()

  private def settings: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)
}

object ScalaGenerateToStringWizard {

  private final class Panel(members: Seq[ScNamedElement])
                           (listener: TableModelListener)
    extends ScalaMemberSelectionPanel(
      ScalaCodeInsightBundle.message("generate.ui.toString.properties"),
      members,
      new AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo] {}
    ) {

    getTable.getModel.addTableModelListener(listener)

    private val checkBox = new JCheckBox(ScalaCodeInsightBundle.message("generate.ui.toString.withNames"))

    add(checkBox, BorderLayout.SOUTH)

    def isSelected: Boolean = checkBox.isSelected

    //noinspection AccessorLikeMethodIsUnit
    def isSelected_=(isSelected: Boolean): Unit = {
      checkBox.setSelected(isSelected)
    }
  }

  private final class ToStringStep(override val getComponent: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo]) extends StepAdapter {

    override def getPreferredFocusedComponent: AbstractMemberSelectionTable[ScNamedElement, ScalaMemberInfo] = getComponent.getTable
  }

}
