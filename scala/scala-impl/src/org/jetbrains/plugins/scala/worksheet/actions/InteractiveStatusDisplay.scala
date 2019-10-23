package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.roots.ScalableIconComponent
import com.intellij.util.ui.{AsyncProcessIcon, EmptyIcon}
import javax.swing.{Icon, JPanel}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentDisplayable
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiConstructor

class InteractiveStatusDisplay extends TopComponentDisplayable {
  import InteractiveStatusDisplay._

  private val myPanel = new JPanel()

  private var currentIcon: AsyncProcessIcon = _

  private val successIcon = createAnimatedIcon(MyOkIcon)
  private val failureIcon = createAnimatedIcon(MyErrorIcon)

  override def init(panel: JPanel): Unit = {
    myPanel.add(createIconWrapper(MyEmptyIcon), 0)
    panel.add(myPanel, 0)
    WorksheetUiConstructor.fixUnboundMaxSize(myPanel)
  }

  def onStartCompiling(): Unit = {
    setCurrentIcon(successIcon)
    currentIcon.resume()
  }

  def onFailedCompiling(): Unit =
    setCurrentIcon(failureIcon)

  def onSuccessfulCompiling(): Unit =
    setCurrentIcon(successIcon)

  private def setCurrentIcon(icon: AsyncProcessIcon): Unit = {
    if (currentIcon != icon) {
      currentIcon = icon
      myPanel.removeAll()
      myPanel.add(icon, 0)
    }
    currentIcon.suspend()
  }

  private def createAnimatedIcon(passiveIcon: Icon): AsyncProcessIcon = {
    val frames = AnimatedIcon.Default.ICONS.toArray(Array.empty[Icon])
    new AsyncProcessIcon("Compiling...", frames, passiveIcon)
  }

  private def createIconWrapper(icon: Icon): ScalableIconComponent =
    new ScalableIconComponent(icon)
}

object InteractiveStatusDisplay {

  private val MyOkIcon    = AllIcons.General.InspectionsOK
  private val MyErrorIcon = AllIcons.General.Error
  private val MyEmptyIcon = EmptyIcon.ICON_18
}
