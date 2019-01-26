package org.jetbrains.plugins.scala
package codeInsight
package delegate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

trait ScalaDelegateMethodTestBase {

  import ScalaDelegateMethodTestBase._
  import util.TypeAnnotationSettings.set

  protected final def doTest(file: PsiFile, settings: ScalaCodeStyleSettings)
                            (implicit project: Project, editor: Editor): Unit = {
    val oldSettings = codeStyleSettings.clone()
    set(project, settings)

    new ScalaGenerateDelegateHandler().invoke(project, editor, file)
    set(project, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
  }
}

object ScalaDelegateMethodTestBase {

  import util.TypeAnnotationSettings.{noTypeAnnotationForPublic => noTypeAnnotation, _}

  def defaultSettings(implicit project: Project): ScalaCodeStyleSettings =
    alwaysAddType(codeStyleSettings)

  def noTypeAnnotationForPublic(implicit project: Project): ScalaCodeStyleSettings =
    noTypeAnnotation(defaultSettings)

  def codeStyleSettings(implicit project: Project): ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(project)
}
