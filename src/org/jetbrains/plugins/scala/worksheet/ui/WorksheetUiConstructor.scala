package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.{Component, Dimension}
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing._

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.components.StopWorksheetAction
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, CopyWorksheetAction, RunWorksheetAction, InteractiveStatusDisplay}
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

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
      
      addChild(panel, Box.createHorizontalGlue())

      if (RunWorksheetAction.isScratchWorksheet(Option(file), project)) {
        addSplitter()
        
        val psiPsiFound = PsiManager getInstance project findFile file
        
        if (psiPsiFound != null) { 
          // this could happen if there is no suitable scala modules in the project, 
          // but we created scratch file and we have scala plugin installed
          Option(RunWorksheetAction.getModuleFor(psiPsiFound)) foreach {
            cpModule =>
              addChild(panel, createSelectClassPathList(Option(cpModule.getName), file))
              addChild(panel, new JLabel("Use class path of module:  "))
          }
        }
      }

      addSplitter()
      addChild(panel, createMakeProjectChb(file))
      addChild(panel, createAutoRunChb(file))

      
      addSplitter()

      new CopyWorksheetAction().init(panel)
      addFiller()
      new CleanWorksheetAction().init(panel)
      addFiller()
      if (run) new RunWorksheetAction().init(panel) else exec foreach (new StopWorksheetAction(_).init(panel))
    }

    Option(statusDisplayN)
  }
  
  private def createSelectClassPathList(defaultModule: Option[String], file: VirtualFile) = {
    val modulesBox = new ModulesComboBox()

    modulesBox fillModules project
    modulesBox.setToolTipText("Using class path of the module...")

    defaultModule foreach {
      nn =>
        val foundModule: Module = ModuleManager getInstance project findModuleByName nn
        if (foundModule != null) modulesBox setSelectedModule foundModule
    }

    modulesBox.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent) {
        val m = modulesBox.getSelectedModule

        if (m == null) return

        WorksheetCompiler.setModuleForCpName(PsiManager getInstance project findFile file, m.getName)
      }
    })

    WorksheetUiConstructor.fixUnboundMaxSize(modulesBox, isSquare = false)  
    
    modulesBox
  }

  def createMakeProjectChb(file: VirtualFile): JCheckBox = {
    createCheckBox(
      "Make project",
      WorksheetCompiler.isMakeBeforeRun(PsiManager getInstance project findFile file),
      box =>  new ChangeListener {
        override def stateChanged(e: ChangeEvent) {
          WorksheetCompiler.setMakeBeforeRun(PsiManager getInstance project findFile file, box.isSelected)
        }
      }
    )
  }

  def createAutoRunChb(file: VirtualFile): JCheckBox = {
    val psiFile = PsiManager getInstance project findFile file

    import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner._
    
    createCheckBox(
      "Interactive Mode",
      if (isSetEnabled(psiFile)) true else if (isSetDisabled(psiFile)) false else ScalaProjectSettings.getInstance(project).isInteractiveMode,
      box => new ChangeListener {
        override def stateChanged(e: ChangeEvent) {
          WorksheetAutoRunner.setAutorun(psiFile, box.isSelected)
        }
      }
    )
  }
  
  private def createCheckBox(title: String, isSelected: Boolean, listener: JCheckBox => ChangeListener) = {
    val box = new JCheckBox(title, isSelected)
    box addChangeListener listener(box)
    box.setAlignmentX(Component.CENTER_ALIGNMENT)
    
    box
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
