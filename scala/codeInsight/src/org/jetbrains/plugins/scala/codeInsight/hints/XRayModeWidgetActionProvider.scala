package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys, DefaultActionGroup, Presentation, Separator}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor.{`lazy` => LazyJBColor}
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.{JBInsets, JBUI, UIUtil}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.settings.{ScalaProjectSettingsConfigurable, ShowSettingsUtilImplExt}

import java.awt.Insets
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

class XRayModeWidgetActionProvider extends InspectionWidgetActionProvider {
  override def createAction(editor: Editor): AnAction = {
    val project = editor.getProject

    if (project == null || project.isDefault) return null

    val action = new DumbAwareAction("X-Ray Mode") with CustomComponentAction {
      override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT

      override def createCustomComponent(presentation: Presentation, place: String): JComponent = new ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
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
            override def mouseClicked(e: MouseEvent): Unit = if (e.getButton == MouseEvent.BUTTON1) {
              click() // Enable Cmd + Click
              e.consume()
            }
          })
        }

        override def iconTextSpace: Int = JBUI.scale(2)

        override def getInsets: Insets = JBUI.insets(2)

        override def getMargins: Insets = JBInsets.addInsets(super.getMargins, JBUI.insetsRight(5))
      }

      override def update(e: AnActionEvent): Unit = {
        e.getPresentation.setDescription("Configure X-Ray Mode...")
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        ShowSettingsUtilImplExt.showSettingsDialog(
          e.getProject,
          classOf[ScalaProjectSettingsConfigurable],
          ScalaBundle.message("scala.project.settings.form.tabs.xray.mode"))
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
