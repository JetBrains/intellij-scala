package org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias

import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog

final class ScalaInlineTypeAliasDialog(typeAlias: ScTypeAliasDefinition)(implicit project: Project)
  extends ScalaInlineDialog(typeAlias, title = ScalaInlineTypeAliasHandler.RefactoringName, helpId = ScalaInlineTypeAliasHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor = new ScalaInlineTypeAliasProcessor(typeAlias)

  override protected def inlineQuestion: String =
    if (typeAlias.isLocal) ScalaBundle.message("inline.local.type.alias.label", typeAlias.name)
    else ScalaBundle.message("inline.type.alias.label", typeAlias.name)
}
