package org.jetbrains.plugins.scala.lang.refactoring.inline.method

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineProcessor

final class ScalaInlineMethodProcessor(method: ScFunctionDefinition)
                                      (implicit project: Project) extends ScalaInlineProcessor(method) {
  override def getCommandName: String = ScalaInlineMethodHandler.RefactoringName

  override protected def removeDefinition(): Unit =
    CodeEditUtil.removeChild(method.getParent.getNode, method.getNode)

  override protected val processedElementsUsageViewHeader: String =
    if (method.isLocal) ScalaBundle.message("inline.local.method.elements.header")
    else ScalaBundle.message("inline.method.elements.header")
}
