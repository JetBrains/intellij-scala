package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.{Component, Dimension}
import javax.swing._

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.components.StopWorksheetAction
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.worksheet.actions._

/**
  * User: Dmitry.Naydanov
  * Date: 22.12.15.
  */
class WorksheetUiConstructor(base: JComponent, project: Project) {
  private val (baseSize, hh, wh) = calculateDeltas(base)

  
  def initTopPanel(panel: JPanel, file: VirtualFile, run: Boolean, exec: Option[CompilationProcess]): Option[InteractiveStatusDisplay] = {
    val layout = new BoxLayout(panel, BoxLayout.LINE_AXIS)
    panel setLayout layout
    panel setAlignmentX 0.0f //leftmost

    import WorksheetUiConstructor._  
    
    @inline def addSplitter(): Unit = addChild(panel, createSplitter())
    @inline def addFiller(): Unit = {
      panel.getComponent(0) match {
        case child: JComponent => 
          addChild(panel, createFillerFor(child))
        case _ => 
      }  
    }
    
    var statusDisplayN: InteractiveStatusDisplay = null
    
    extensions.inReadAction {
      statusDisplayN = new InteractiveStatusDisplay()
      statusDisplayN.init(panel)
      if (run) statusDisplayN.onSuccessfulCompiling() else statusDisplayN.onStartCompiling()
      addSplitter()
      addChild(panel, Box.createHorizontalGlue())
      addSplitter()

      new ShowWorksheetSettingsAction().init(panel)
      addSplitter()
      new CopyWorksheetAction().init(panel)
      addFiller()
      new CleanWorksheetAction().init(panel)
      addFiller()
      if (run) new RunWorksheetAction().init(panel) else exec foreach (new StopWorksheetAction(_).init(panel))
    }

    Option(statusDisplayN)
  }
 
  
  private def calculateDeltas(comp: JComponent) = {
    val baseSize = comp.getPreferredSize
    val hh = baseSize.height / 5
    val wh = baseSize.width / 5
    
    (baseSize, hh, wh)
  }
  
  private def createFillerFor(comp: JComponent) = {
    val (baseSize, hh, wh) = calculateDeltas(comp)
    
    WorksheetUiConstructor.createFiller(baseSize.height + hh, wh)
  }
  
  def createFiller(): Component = WorksheetUiConstructor.createFiller(baseSize.height + hh, wh)
}

object WorksheetUiConstructor {
  def fixUnboundMaxSize(comp: JComponent, isSquare: Boolean = true) {
    val preferredSize = comp.getPreferredSize
    
    val size = if (isSquare) {
      val sqSize = Math.max(preferredSize.width, preferredSize.height)
      new Dimension(sqSize, sqSize)
    } else new Dimension(preferredSize.width, preferredSize.height)
    
    comp setMaximumSize size
  }

  def addChild(parent: JComponent, child: Component, idx: Int = 0) {
    parent.add(child, 0)  
  }

  def createSplitter(): JSeparator = {
    val separator = new JSeparator(SwingConstants.VERTICAL)
    val size = new Dimension(separator.getPreferredSize.width, separator.getMaximumSize.height)
    separator setMaximumSize size

    separator
  }

  def createFiller(h: Int, w: Int): Component = Box.createRigidArea(new Dimension(w, h))
}
