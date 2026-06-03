package org.jetbrains.plugins.scala.lang.refactoring.inline.variable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

final class ScalaInlineVariableDialog(pattern: ScBindingPattern, variable: ScValueOrVariableDefinition)(implicit project: Project, editor: Editor)
  extends ScalaInlineDialog(pattern, title = ScalaInlineVariableHandler.RefactoringName, helpId = ScalaInlineVariableHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor =
    new ScalaInlineVariableProcessor(
      pattern,
      variable,
      reference,
      inlineThisOnly = isInlineThisOnly,
      removeDefinition = pattern.isWritable && !isKeepTheDeclaration,
    )

  override protected def inlineThisGetter: ScalaApplicationSettings => Boolean = _.INLINE_VARIABLE_THIS
  override protected def inlineThisSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_VARIABLE_THIS = _

  override protected def inlineKeepGetter: ScalaApplicationSettings => Boolean = _.INLINE_VARIABLE_KEEP
  override protected def inlineKeepSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_VARIABLE_KEEP = _

  override def getNameLabelText: String =
    if (variable.isLocal) ScalaBundle.message("inline.local.variable.name.label", pattern.name)
    else ScalaBundle.message("inline.variable.name.label", pattern.name)

  override def getInlineAllUsagesAndRemoveDefinitionText: String =
    ScalaBundle.message("inline.all.usages.and.remove.the.variable")

  override def getInlineThisText: String = ScalaBundle.message("inline.this.usage.only.and.keep.the.variable")

  override def getKeepTheDeclarationText: String =
    if (pattern.isWritable) ScalaBundle.message("inline.all.usages.and.keep.the.variable")
    else null
}
