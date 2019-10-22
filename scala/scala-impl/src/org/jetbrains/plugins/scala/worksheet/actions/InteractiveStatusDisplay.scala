package org.jetbrains.plugins.scala.worksheet.actions

import java.awt.geom.AffineTransform
import java.awt.{Color, Component, Graphics, Graphics2D}

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.roots.ScalableIconComponent
import com.intellij.util.ui.{AnimatedIcon, EmptyIcon}
import javax.swing.border.LineBorder
import javax.swing.{Icon, JComponent, JPanel}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentDisplayable
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiConstructor

/**
  * User: Dmitry.Naydanov
  * Date: 23.11.15.
  */
class InteractiveStatusDisplay extends TopComponentDisplayable {
  import InteractiveStatusDisplay._
  
  private val myPanel = new JPanel()
  
  private val successIcon = createAnimatedIcon(InteractiveStatusDisplay.MY_COMPILE_ACTION, MY_OK_ICON, ICON_STEP_COUNT)
  private val failIcon = createAnimatedIcon(InteractiveStatusDisplay.MY_COMPILE_ACTION, MY_ERROR_ICON, ICON_STEP_COUNT)
  
  private var current: AnimatedIcon = null
  
  override def init(panel: JPanel): Unit = {
    myPanel.add(createSimpleIcon(EMPTY_ICON), 0)
    panel.add(myPanel, 0)
    setBorder(OTHER_BORDER)
    WorksheetUiConstructor.fixUnboundMaxSize(myPanel)  
  }
  
  def onStartCompiling() {
    setCurrentIcon(successIcon)
    current.resume()
    setBorder(OTHER_BORDER)
  }
  
  def onFailedCompiling() {
    setCurrentIcon(failIcon)
    current.suspend()
    setBorder(ERROR_BORDER)
  }
  
  def onSuccessfulCompiling() {
    setCurrentIcon(successIcon)
    current.suspend()
    setBorder(OK_BORDER)
  }
  
  private def setBorder(border: LineBorder) {
    if (isBorderEnabled) ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = myPanel.setBorder(border)  
    })    
  }
  
  private def isBorderEnabled = false //right now we don't need it (?) 
  
  private def setCurrentIcon(icon: AnimatedIcon) {
    if (current == icon) return 
    
    current = icon
    clearAndAdd()
  }
  
  private def clearAndAdd() {
    myPanel.removeAll()
    myPanel.add(current, 0)
  }
  
  private def createSimpleIcon(icon: Icon): JComponent = new ScalableIconComponent(icon)
  
  private def createAnimatedIcon(icon1: Icon, icon2: Icon, steps: Int): AnimatedIcon = {
    val pi2 = Math.PI*2
    val step = pi2/steps
    
    val i = new AnimatedIcon(
      "Compiling...", 
      (for (i <- 0 to steps) yield new RotatedIcon(icon1, step * i)).toArray[Icon], 
      icon2, ICON_CYCLE_LENGTH
    )
    i.resume()
    i
  }
}

object InteractiveStatusDisplay {
  private val MY_ERROR_ICON = AllIcons.General.Error
  private val MY_OK_ICON = AllIcons.General.InspectionsOK
  private val MY_COMPILE_ACTION = AllIcons.Actions.ForceRefresh 
  private val EMPTY_ICON = EmptyIcon.ICON_18
  
  private val ICON_CYCLE_LENGTH = 1300
  private val ICON_STEP_COUNT = 10
  
  private val OK_BORDER = new LineBorder(Color.GREEN)
  private val ERROR_BORDER = new LineBorder(Color.RED)
  private val OTHER_BORDER = new LineBorder(Color.LIGHT_GRAY)
  
  private class RotatedIcon(delegate: Icon, rotation: Double) extends Icon {
    override def getIconHeight: Int = delegate.getIconWidth

    override def paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      val xn = getIconWidth / 2 + x
      val yn = getIconHeight / 2 + y
      
      val tx = AffineTransform.getRotateInstance(Math.PI*2 - rotation, xn, yn)
      g.asInstanceOf[Graphics2D].setTransform(tx)
      
      delegate.paintIcon(c, g, x, y)
    }

    override def getIconWidth: Int = delegate.getIconHeight
  }
}
