package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.Dimension

import com.intellij.openapi.vfs.VirtualFile
import javax.swing._
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.worksheet.actions.InteractiveStatusDisplay
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess
import org.jetbrains.plugins.scala.worksheet.actions.topmenu._
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetControlPanel._

class WorksheetControlPanel(private val file: VirtualFile) extends JPanel {

  private val statusDisplay = new InteractiveStatusDisplay()
  private val runAction = new RunWorksheetAction()
  private val stopAction = new StopWorksheetAction(None)
  private val cleanAction = new CleanWorksheetAction()
  private val copyAction = new CopyWorksheetAction()
  private val settingsAction = new ShowWorksheetSettingsAction()

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
      panel.add(Box.createHorizontalGlue())
      addSplitter()
      statusDisplay.init(panel)
      statusDisplay.onSuccessfulCompiling()

      enableRun(false)
    }
  }

  def enableRun(hasErrors: Boolean): Unit = {
    stopAction.setEnabled(false)
    stopAction.setVisible(false)
    stopAction.setStoppableProcess(None)
    runAction.setEnabled(true)
    runAction.setVisible(true)

    if (hasErrors) {
      statusDisplay.onFailedCompiling()
    } else {
      statusDisplay.onSuccessfulCompiling()
    }
  }

  def disableRun(exec: Option[StoppableProcess]): Unit = {
    runAction.setEnabled(false)
    runAction.setVisible(false)
    stopAction.setEnabled(true)
    stopAction.setVisible(true)
    stopAction.setStoppableProcess(exec)

    statusDisplay.onStartCompiling()
  }

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
