package org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineProcessor

final class ScalaInlineTypeAliasProcessor(typeAlias: ScTypeAliasDefinition)
                                         (implicit project: Project) extends ScalaInlineProcessor(typeAlias) {
  override def getCommandName: String = ScalaInlineTypeAliasHandler.RefactoringName

  override protected def removeDefinition(): Unit =
    removeElementWithNonSignificantSiblings(typeAlias)

  override protected val processedElementsUsageViewHeader: String =
    if (typeAlias.isLocal) ScalaBundle.message("inline.local.type.alias.elements.header")
    else ScalaBundle.message("inline.type.alias.elements.header")
}
