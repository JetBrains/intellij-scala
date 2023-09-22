package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.plugins.scala.DesktopUtils
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.awt.{BorderLayout, FlowLayout}
import javax.swing.{JComponent, JPanel}

//noinspection UnstableApiUsage
class TypeMismatchHintsSettingsModel(project: Project)
  extends BooleanTypeHintSettingsModelBase(project, "Scala.TypeMismatchHintsSettingsModel")
{
  override protected def getSetting: Boolean =
    ScalaProjectSettings.getInstance(project).isTypeMismatchHints
  override protected def setSetting(value: Boolean): Unit =
    ScalaProjectSettings.getInstance(project).setTypeMismatchHints(value)

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    // TODO: add preview text and make inlays work in the preview
  }

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.type.mismatch.hints")

  override def getName: String = ScalaCodeInsightBundle.message("type.mismatch.hints")

  override def getPreviewText: String = null

  override def getDescription: String = ScalaCodeInsightBundle.message("show.type.mismatch.hints")

  override val getComponent: JComponent = {
    val linePanel = {
      val link = new LinkLabel[Any](ScalaCodeInsightBundle.message("link.label.more.info"), null)
      val url = "https://blog.jetbrains.com/scala/2019/07/02/functional-highlighting-for-functional-programming/"
      link.setToolTipText(url)
      link.setListener((_, _) => DesktopUtils.browse(project, url), null)
      val linePanel = {
        val layout = new FlowLayout()
        layout.setHgap(0)
        layout.setAlignment(FlowLayout.LEFT)
        new JPanel(layout)
      }
      linePanel.add(link)
      linePanel
    }

    val panel = new JPanel(new BorderLayout())
    panel.add(linePanel, BorderLayout.NORTH)
    panel
  }
}
