package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.plugins.scala.annotator.TypeMismatchHints
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.{DesktopUtils, ScalaLanguage}

import java.awt.{BorderLayout, FlowLayout}
import java.util
import javax.swing.{JComponent, JPanel}

//noinspection UnstableApiUsage
class TypeMismatchHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(
  true,
  "Scala.TypeMismatchHintsSettingsModel",
  ScalaLanguage.INSTANCE
) {
  override def getGroup: InlayGroup = InlayGroup.TYPES_GROUP

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

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.type.mismatch.hints")

  override def getName: String = ScalaCodeInsightBundle.message("type.mismatch.hints")

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

  override def getDescription: String = ScalaCodeInsightBundle.message("show.type.mismatch.hints")

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}
