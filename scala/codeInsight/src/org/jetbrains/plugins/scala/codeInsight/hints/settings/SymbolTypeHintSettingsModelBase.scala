package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

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
}
