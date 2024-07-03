package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.{ActionButtonWithText, ActionToolbarImpl}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys, DefaultActionGroup, Presentation, Separator}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.{IconLoader, SystemInfo}
import com.intellij.ui.JBColor.{`lazy` => LazyJBColor}
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.{JBInsets, JBUI, UIUtil}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.settings.XRayWidgetMode
import org.jetbrains.plugins.scala.settings.sections.XRayModeSettingsSectionConfigurable

import java.awt.event.{InputEvent, MouseAdapter, MouseEvent}
import java.awt.{Component, Insets, Toolkit}
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

        override def updateToolTipText(): Unit = {
          HelpTooltip.dispose(this)
          new HelpTooltip()
            .setTitle(
              if (ScalaHintsSettings.xRayMode)
                if (ScalaHintsSettings.xRayModePinned) ScalaCodeInsightBundle.message("xray.mode.widget.tooltip.exit")
                else ScalaCodeInsightBundle.message("xray.mode.widget.tooltip.pin")
              else
                ScalaCodeInsightBundle.message("xray.mode.widget.tooltip.enter"))
            .setDescription(if (ScalaHintsSettings.xRayMode) null else "(" + ScalaHintsSettings.xRayModeShortcut + ")")
            .setLink(
              ScalaCodeInsightBundle.message("xray.mode.widget.tooltip.link"),
              () => ShowSettingsUtil.getInstance.showSettingsDialog(project, classOf[XRayModeSettingsSectionConfigurable])
            )
            .installOn(this)
        }

        override def iconTextSpace: Int = JBUI.scale(2)

        override def getInsets: Insets = JBUI.insets(2)

        override def getMargins: Insets = JBInsets.addInsets(super.getMargins, JBUI.insetsRight(5))
      }

      override def update(e: AnActionEvent): Unit = {
        if (ScalaHintsSettings.xRayMode) {
          e.getPresentation.setText(ScalaCodeInsightBundle.message("xray.mode.widget.text"))
          e.getPresentation.setIcon(
            if (ScalaHintsSettings.xRayModePinned) Icons.PIN
            else IconLoader.getDisabledIcon(Icons.PIN))
        } else {
          e.getPresentation.setText(null: String)
          e.getPresentation.setIcon(IconLoader.getDisabledIcon(AllIcons.Actions.Show))
        }
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        if (ScalaHintsSettings.xRayMode) {
          if (ScalaHintsSettings.xRayModePinned) {
            ScalaEditorFactoryListener.setXRayModeEnabled(false, e.getData(CommonDataKeys.EDITOR))
          }
          ScalaHintsSettings.xRayModePinned = !ScalaHintsSettings.xRayModePinned
        } else {
          ScalaEditorFactoryListener.setXRayModeEnabled(true, e.getData(CommonDataKeys.EDITOR))
          ScalaHintsSettings.xRayModePinned = true
        }
        ActionToolbarImpl.updateAllToolbarsImmediately(false) // Update the tooltip
      }
    }

    new DefaultActionGroup(action, Separator.create()) {
      override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

      override def update(e: AnActionEvent): Unit = {
        def inLibrarySource = Option(CommonDataKeys.EDITOR.getData(e.getDataContext)).exists { editor =>
          Option(editor.getVirtualFile).exists(file => ProjectFileIndex.getInstance(editor.getProject).isInLibrarySource(file))
        }
        val visible = ScalaApplicationSettings.XRAY_WIDGET_MODE match {
          case XRayWidgetMode.ALWAYS => !inLibrarySource
          case XRayWidgetMode.WHEN_ACTIVE => ScalaHintsSettings.xRayMode && !inLibrarySource
          case XRayWidgetMode.NEVER => false
        }
        e.getPresentation.setEnabledAndVisible(visible)
      }
    }
  }
}
