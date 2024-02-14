package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.roots.ScalableIconComponent
import com.intellij.util.ui.{AsyncProcessIcon, EmptyIcon}
import org.jetbrains.plugins.scala.worksheet.actions.InteractiveStatusDisplay.{createAnimatedIcon, createIconWrapper}
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiUtils

import javax.swing.{Icon, JPanel}

class InteractiveStatusDisplay {

  private val iconPanel = new JPanel()

  private var currentIcon: Option[AsyncProcessIcon] = None

  private val successIcon = createAnimatedIcon(AllIcons.General.InspectionsOK)
  private val failureIcon = createAnimatedIcon(AllIcons.General.Error)

  def addTo(panel: JPanel): Unit = {
    iconPanel.add(createIconWrapper(EmptyIcon.ICON_18), 0)
    panel.add(iconPanel)
    WorksheetUiUtils.fixUnboundMaxSize(iconPanel)
  }

  def onStartCompiling(): Unit = {
    setCurrentIcon(successIcon)
    currentIcon.foreach(_.resume())
  }

  def onFailedCompiling(): Unit =
    setCurrentIcon(failureIcon)

  def onSuccessfulCompiling(): Unit =
    setCurrentIcon(successIcon)

  private def setCurrentIcon(icon: AsyncProcessIcon): Unit = {
    if (!currentIcon.contains(icon)) {
      currentIcon = Some(icon)
      iconPanel.removeAll()
      iconPanel.add(icon, 0)
    }
    currentIcon.foreach(_.suspend())
  }
}

private object InteractiveStatusDisplay {

  private def createIconWrapper(icon: Icon): ScalableIconComponent =
    new ScalableIconComponent(icon)

  private def createAnimatedIcon(passiveIcon: Icon): AsyncProcessIcon = {
    val frames = AnimatedIcon.Default.ICONS.toArray(Array.empty[Icon])
    new AsyncProcessIcon("Compiling...", frames, passiveIcon)
  }
}
