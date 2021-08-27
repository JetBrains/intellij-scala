package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class DfaConstantConditionsVisitor(problemsHolder: ProblemsHolder) extends ScalaRecursiveElementVisitor {

  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
    // TODO add behaviour
  }
}
