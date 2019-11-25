package org.jetbrains.plugins.scala.codeInsight.hints

import java.awt.{BorderLayout, FlowLayout}
import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.labels.LinkLabel
import javax.swing.{JComponent, JLabel, JPanel}
import org.jetbrains.plugins.scala.DesktopUtils
import org.jetbrains.plugins.scala.annotator.TypeMismatchHints
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeMismatchHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(true, "Scala.TypeMismatchHintsSettingsModel") {
  object settings {
    private val global = ScalaProjectSettings.getInstance(project)

    reset()

    def reset(): Unit = {
      setEnabled(global.isTypeMismatchHints)
    }

    def apply(): Unit = {
      global.setTypeMismatchHints(isEnabled)
    }

    def isModified: Boolean =
      global.isTypeMismatchHints != isEnabled
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = util.Collections.emptyList()

  override val getComponent: JComponent = {
    val linePanel = {
      val link = new LinkLabel[Any]("more info", null)
      val url = "https://blog.jetbrains.com/scala/2019/07/02/functional-highlighting-for-functional-programming/"
      link.setToolTipText(url)
      link.setListener((_, _) => DesktopUtils.browse(url), null)
      val linePanel = {
        val layout = new FlowLayout()
        layout.setHgap(0)
        layout.setAlignment(FlowLayout.LEFT)
        new JPanel(layout)
      }
      linePanel.add(new JLabel("(instead of underlining the code, "))
      linePanel.add(link)
      linePanel.add(new JLabel(")"))
      linePanel
    }

    val panel = new JPanel(new BorderLayout())
    panel.add(linePanel, BorderLayout.NORTH)
    panel
  }

  override def getMainCheckBoxLabel: String = "Show type mismatch hints"

  override def getName: String = "Type mismatch hints"

  override def getPreviewText: String = null

  override def apply(): Unit = {
    settings.apply()
    TypeMismatchHints.refreshIn(project)
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = ()

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = {
    settings.reset()
  }
}
