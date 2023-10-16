package org.jetbrains.plugins.scala.lang.refactoring.inline.method

import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog

final class ScalaInlineMethodDialog(method: ScFunctionDefinition)(implicit project: Project)
  extends ScalaInlineDialog(method, title = ScalaInlineMethodHandler.RefactoringName, helpId = ScalaInlineMethodHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor = new ScalaInlineMethodProcessor(method)

  override protected def inlineQuestion: String =
    if (method.isLocal) ScalaBundle.message("inline.local.method.label", method.name)
    else ScalaBundle.message("inline.method.label", method.name)
}
