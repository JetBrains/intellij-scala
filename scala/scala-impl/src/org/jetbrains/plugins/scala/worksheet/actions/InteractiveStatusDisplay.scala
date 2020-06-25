package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.roots.ScalableIconComponent
import com.intellij.util.ui.{AsyncProcessIcon, EmptyIcon}
import javax.swing.{Icon, JPanel}
import org.jetbrains.plugins.scala.worksheet.actions.InteractiveStatusDisplay.{createAnimatedIcon, createIconWrapper}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentDisplayable
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiUtils

class InteractiveStatusDisplay extends TopComponentDisplayable {

  private val iconPanel = new JPanel()

  private var currentIcon: Option[AsyncProcessIcon] = None

  private val successIcon = createAnimatedIcon(AllIcons.General.InspectionsOK)
  private val failureIcon = createAnimatedIcon(AllIcons.General.Error)

  override def init(panel: JPanel): Unit = {
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