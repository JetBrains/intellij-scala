package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, DefaultActionGroup}
import org.jetbrains.plugins.scala.worksheet.actions.InteractiveStatusDisplay
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess
import org.jetbrains.plugins.scala.worksheet.actions.topmenu._
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetControlPanel.createSplitter

import java.awt.Dimension
import javax.swing._
import scala.util.chaining.scalaUtilChainingOps

// TODO: check if Scala Plugin is unloadable if there are some worksheets with initialized top panel UI
final class WorksheetControlPanel extends JPanel {

  private val statusDisplay = new InteractiveStatusDisplay()
  private val runAction = new RunWorksheetAction()
  private val stopAction = new StopWorksheetAction(None)
  private val cleanAction = new CleanWorksheetAction()
  private val copyAction = new CopyWorksheetAction()
  private val settingsAction = new ShowWorksheetSettingsAction()

  private var runEnabled = false

  locally {
    val panel = this

    val layout = new BoxLayout(panel, BoxLayout.LINE_AXIS)
    panel.setLayout(layout)
    panel.setAlignmentX(0.0f) //leftmost

    val leftToolbarGroup = new DefaultActionGroup().tap { group =>
      group.add(runAction)
      group.add(stopAction)
      group.add(cleanAction)
      group.add(copyAction)
      group.addSeparator()
      group.add(settingsAction)
      group.addSeparator()
    }
    val leftToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, leftToolbarGroup, true)
    leftToolbar.setTargetComponent(panel)

    panel.add(leftToolbar.getComponent)
    panel.add(Box.createHorizontalGlue())
    panel.add(createSplitter())
    statusDisplay.addTo(panel)

    enableRun(hasErrors = false)
  }

  def enableRun(hasErrors: Boolean): Unit = {
    runEnabled = true
    stopAction.setStoppableProcess(None)

    if (hasErrors) {
      statusDisplay.onFailedCompiling()
    } else {
      statusDisplay.onSuccessfulCompiling()
    }
  }

  def disableRun(exec: Option[StoppableProcess]): Unit = {
    runEnabled = false
    stopAction.setStoppableProcess(exec)

    statusDisplay.onStartCompiling()
  }

  def isRunEnabled: Boolean = runEnabled

  def updateStoppableProcess(exec: Option[StoppableProcess]): Unit =
    stopAction.setStoppableProcess(exec)
}

object WorksheetControlPanel {

  private def createSplitter(): JSeparator = {
    val separator = new JSeparator(SwingConstants.VERTICAL)
    val size = new Dimension(separator.getPreferredSize.width, separator.getMaximumSize.height)
    separator.setMaximumSize(size)
    separator
  }
}
