package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.Dimension

import javax.swing._
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.worksheet.WorksheetCompilerExtension
import org.jetbrains.plugins.scala.worksheet.actions.InteractiveStatusDisplay
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess
import org.jetbrains.plugins.scala.worksheet.actions.topmenu._
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetControlPanel._

// TODO: check if Scala Plugin is unloadable if there are some worksheets with initialized top panel UI
final class WorksheetControlPanel extends JPanel {

  private val statusDisplay = new InteractiveStatusDisplay()
  private val runAction = new RunWorksheetAction()
  private val stopAction = new StopWorksheetAction(None)
  private val cleanAction = new CleanWorksheetAction()
  private val copyAction = new CopyWorksheetAction()
  private val settingsAction = new ShowWorksheetSettingsAction()
  private val extraActions = WorksheetCompilerExtension.extraWorksheetActions()

  private var runEnabled = false

  init()

  private def init(): Unit = {
    val panel = this

    val layout = new BoxLayout(panel, BoxLayout.LINE_AXIS)
    panel.setLayout(layout)
    panel.setAlignmentX(0.0f) //leftmost

    @inline def addSplitter(): Unit =
      panel.add(createSplitter())

    @inline def addFiller(): Unit = {
      val lastComponent = panel.getComponent(panel.getComponentCount - 1)
      lastComponent match {
        case child: JComponent =>
          panel.add(createFillerFor(child))
        case _                 =>
      }
    }

    inReadAction {
      runAction.init(panel)
      stopAction.init(panel)
      addFiller()
      cleanAction.init(panel)
      addFiller()
      copyAction.init(panel)
      addSplitter()
      settingsAction.init(panel)
      addSplitter()
      extraActions.foreach { action =>
        action.init(panel)
      }
      panel.add(Box.createHorizontalGlue())
      addSplitter()
      statusDisplay.init(panel)
      statusDisplay.onSuccessfulCompiling()

      enableRun(hasErrors = false)
    }
  }

  def enableRun(hasErrors: Boolean): Unit = {
    runEnabled = true
    stopAction.setStoppableProcess(None)
    updateView()

    if (hasErrors) {
      statusDisplay.onFailedCompiling()
    } else {
      statusDisplay.onSuccessfulCompiling()
    }
  }

  def disableRun(exec: Option[StoppableProcess]): Unit = {
    runEnabled = false
    stopAction.setStoppableProcess(exec)
    updateView()

    statusDisplay.onStartCompiling()
  }

  private def updateView(): Unit = {
    stopAction.setEnabled(!runEnabled)
    stopAction.setVisible(!runEnabled)
    runAction.setEnabled(runEnabled)
    runAction.setVisible(runEnabled)
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

  private def createFillerFor(comp: JComponent) = {
    val (baseSize, hh, wh) = calculateDeltas(comp)
    Box.createRigidArea(new Dimension(wh, baseSize.height + hh))
  }

  private def calculateDeltas(comp: JComponent) = {
    val baseSize = comp.getPreferredSize
    val hh = baseSize.height / 5
    val wh = baseSize.width / 5

    (baseSize, hh, wh)
  }
}
