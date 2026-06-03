package org.jetbrains.plugins.scala.codeInsight.delegate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

abstract class ScalaDelegateMethodTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import ScalaDelegateMethodTestBase._

  protected def doTest(
    fileText: String,
    expectedText: String,
    settings: ScalaCodeStyleSettings = defaultSettings(getProject)
  ): Unit = {
    configureFromFileText("dummy.scala", fileText)

    implicit val editor: Editor = getEditor
    implicit val project: Project = getProject
    invokeScalaGenerateDelegateHandler(getFile, settings)

    myFixture.checkResult(expectedText.withNormalizedSeparator)
  }

  protected final def invokeScalaGenerateDelegateHandler(
    file: PsiFile,
    settings: ScalaCodeStyleSettings
  )(implicit project: Project, editor: Editor): Unit = {
    val oldSettings = codeStyleSettings.clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(project, settings)

    new ScalaGenerateDelegateHandler().invoke(project, editor, file)

    TypeAnnotationSettings.set(project, oldSettings)
  }
}

object ScalaDelegateMethodTestBase {

  def defaultSettings(implicit project: Project): ScalaCodeStyleSettings =
    TypeAnnotationSettings.alwaysAddType(codeStyleSettings)

  def noTypeAnnotationForPublic(implicit project: Project): ScalaCodeStyleSettings =
    TypeAnnotationSettings.noTypeAnnotationForPublic(defaultSettings)

  def codeStyleSettings(implicit project: Project): ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(project)
}
