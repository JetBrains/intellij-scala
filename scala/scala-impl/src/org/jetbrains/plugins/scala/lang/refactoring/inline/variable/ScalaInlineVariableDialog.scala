package org.jetbrains.plugins.scala.lang.refactoring.inline.variable

import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog

final class ScalaInlineVariableDialog(pattern: ScBindingPattern, variable: ScValueOrVariableDefinition)(implicit project: Project)
  extends ScalaInlineDialog(pattern, title = ScalaInlineVariableHandler.RefactoringName, helpId = ScalaInlineVariableHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor = new ScalaInlineVariableProcessor(pattern, variable)

  override protected def inlineQuestion: String =
    if (variable.isLocal) ScalaBundle.message("inline.local.variable.label", pattern.name)
    else ScalaBundle.message("inline.variable.label", pattern.name)
}
