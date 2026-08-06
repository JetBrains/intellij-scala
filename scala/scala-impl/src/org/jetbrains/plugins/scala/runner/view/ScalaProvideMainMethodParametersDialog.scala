package org.jetbrains.plugins.scala.runner.view

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters.CustomParameter

import javax.swing.JComponent

private[runner] final class ScalaProvideMainMethodParametersDialog(
  project: Project,
  expectedParameters: Seq[CustomParameter],
  actualParameters: Seq[String]
) extends DialogWrapper(project, true, true) {

  assert(expectedParameters.nonEmpty)
  assert(actualParameters.size <= expectedParameters.size)

  private val parametersTable =
    new ScalaMainMethodParametersTable(expectedParameters, actualParameters)

  locally {
    setTitle(ScalaBundle.message("provide.program.arguments"))
    init()
  }

  def filledParametersValues: Seq[String] = parametersTable.filledParametersValues

  override def createCenterPanel(): JComponent = parametersTable.getComponent

  override def doOKAction(): Unit = {
    // needs to be called when we press Enter to invoke "Ok" action, otherwise last edited cell value will not applied
    parametersTable.finishCellEditing()
    super.doOKAction()
  }
}
