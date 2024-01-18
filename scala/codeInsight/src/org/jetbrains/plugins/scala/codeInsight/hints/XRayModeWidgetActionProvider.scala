package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys, DefaultActionGroup, Presentation, Separator}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor.{`lazy` => LazyJBColor}
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.{JBInsets, JBUI, UIUtil}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsConfigurable

import java.awt.event.{InputEvent, MouseAdapter, MouseEvent}
import java.awt.{Component, Insets, Toolkit}
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

class XRayModeWidgetActionProvider extends InspectionWidgetActionProvider {
  override def createAction(editor: Editor): AnAction = {
    val project = editor.getProject
    if (project == null || project.isDefault) return null

    val file = editor.getVirtualFile
    if (file == null || file.getExtension != "scala" && file.getExtension != "sc" && file.getExtension != "sbt") return null

    val action = new DumbAwareAction(ScalaCodeInsightBundle.message("xray.mode.widget.text")) with CustomComponentAction {
      override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT

      override def createCustomComponent(presentation: Presentation, place: String): JComponent = new ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
        addMouseListener(new MouseAdapter {
          override def mousePressed(e: MouseEvent): Unit = {
            val isModifierKeyDown = if (SystemInfo.isMac) e.isMetaDown else e.isControlDown
            if (isModifierKeyDown) {
              e.consume()
              val modifierKeyMask = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
              val event = new MouseEvent(e.getSource.asInstanceOf[Component],
                e.getID, e.getWhen, e.getModifiersEx & ~modifierKeyMask,
                e.getX, e.getY, e.getXOnScreen, e.getYOnScreen, e.getClickCount, e.isPopupTrigger,
                e.getButton)
              Toolkit.getDefaultToolkit.getSystemEventQueue.postEvent(event)
            }
          }
        })

        setForeground(LazyJBColor { () =>
          val IconTextForegroundKey = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground)
          Option(editor.getColorsScheme.getColor(IconTextForegroundKey)).orElse(Option(IconTextForegroundKey.getDefaultColor)).getOrElse(UIUtil.getInactiveTextColor)
        })

        if (!SystemInfo.isWindows) {
          val font = getFont
          setFont(new FontUIResource(font.deriveFont(font.getStyle, font.getSize - JBUIScale.scale(2).toFloat)))
        }

        if (SystemInfo.isMac) {
          addMouseListener(new MouseAdapter() {
            override def mouseClicked(e: MouseEvent): Unit = if (e.getButton == MouseEvent.BUTTON1 && e.isMetaDown) {
              click()
              e.consume()
            }
          })
        }

        override def iconTextSpace: Int = JBUI.scale(2)

        override def getInsets: Insets = JBUI.insets(2)

        override def getMargins: Insets = JBInsets.addInsets(super.getMargins, JBUI.insetsRight(5))
      }

      override def update(e: AnActionEvent): Unit = {
        e.getPresentation.setDescription((ScalaCodeInsightBundle.message("xray.mode.widget.description")))
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        ShowSettingsUtil.getInstance.showSettingsDialog(
          e.getProject,
          classOf[ScalaProjectSettingsConfigurable],
          (_.selectXRayModeTab()): Consumer[ScalaProjectSettingsConfigurable])
      }
    }

    new DefaultActionGroup(action, Separator.create()) {
      override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

      override def update(e: AnActionEvent): Unit = {
        def inLibrarySource = Option(CommonDataKeys.EDITOR.getData(e.getDataContext)).exists { editor =>
          Option(editor.getVirtualFile).exists(file => ProjectFileIndex.getInstance(editor.getProject).isInLibrarySource(file))
        }
        e.getPresentation.setEnabledAndVisible(ScalaHintsSettings.xRayMode && !inLibrarySource)
      }
    }
  }
}
