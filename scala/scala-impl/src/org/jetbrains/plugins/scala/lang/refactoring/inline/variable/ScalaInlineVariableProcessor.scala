package org.jetbrains.plugins.scala.lang.refactoring.inline.variable

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineProcessor

final class ScalaInlineVariableProcessor(pattern: ScBindingPattern, variable: ScValueOrVariableDefinition)
                                        (implicit project: Project) extends ScalaInlineProcessor(pattern) {
  override def getCommandName: String = ScalaInlineVariableHandler.RefactoringName

  override protected def removeDefinition(): Unit =
    removeElementWithNonSignificantSiblings(variable)

  override protected val processedElementsUsageViewHeader: String =
    if (variable.isLocal) ScalaBundle.message("inline.local.variable.elements.header")
    else ScalaBundle.message("inline.variable.elements.header")
}
