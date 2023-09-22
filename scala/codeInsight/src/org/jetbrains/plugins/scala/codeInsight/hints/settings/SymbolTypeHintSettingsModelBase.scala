package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.SwingHelper
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import javax.swing.{JComponent, JPanel}

abstract class SymbolTypeHintSettingsModelBase(group: TypeHintSettingsModelGroup, project: Project, id: String)
  extends BooleanTypeHintSettingsModelBase(project, id)
{
  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    val settings = group.makePreviewSettings()
    val previewPass = new ImplicitHintsPass(editor, psiFile.asInstanceOf[ScalaFile], settings, isPreviewPass = true)
    previewPass.doCollectInformation(DumbProgressIndicator.INSTANCE)
    previewPass.doApplyInformationToEditor()
  }

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |class Person {
      |  val birthYear = 5 + 5
      |
      |  def ageInYear(year: Int) = {
      |    val diff = year - birthYear
      |    math.max(0, diff)
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator.trim
  }

  override def getComponent: JComponent = {
    val settingsInst = ScalaCodeInsightSettings.getInstance()

    @Nls
    def text =
      if (settingsInst.showTypeHints) ScalaCodeInsightBundle.message("show.type.hints.on")
      else ScalaCodeInsightBundle.message("show.type.hints.off")
    val link = new LinkLabel[Null](text, null)
    link.setListener((_, _) => {
      settingsInst.showTypeHints = !settingsInst.showTypeHints
      link.setText(text)
    }, null)

    val linkPanel = new JPanel()
    linkPanel.add(new JBLabel(ScalaCodeInsightBundle.message("type.hints.are.currently")))
    linkPanel.add(link)


    val panel = new JPanel(new MigLayout("wrap, gapy 10, fillx"))
    panel.add(SwingHelper.createHtmlLabel(ScalaCodeInsightBundle.message("type.hints.action.info.text"), null, null))
    panel.add(linkPanel)
    panel
  }
}
