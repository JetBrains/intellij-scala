package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.scala.actions.ScalaActionUtil

/**
 * User: Dmitry.Naydanov
 * Date: 26.05.14.
 */
class CreateLightWorksheetAction extends AnAction {
  override def actionPerformed(e: AnActionEvent) {
    PsiFileFactory.getInstance(e.getProject).createFileFromText("dummy." + ScalaFileType.WORKSHEET_EXTENSION,
      ScalaFileType.SCALA_LANGUAGE, "").navigate(true)
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}
