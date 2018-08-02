package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

package object quickfix {

  private[methodSignature] def removeTypeElement(function: ScFunction): Unit = for {
    colon <- function.children.find(_.getNode.getElementType == ScalaTokenTypes.tCOLON)
    typeElement <- function.returnTypeElement
  } function.deleteChildRange(colon, typeElement)

  private[methodSignature] def removeAssignment(definition: ScFunctionDefinition): Unit = {
    for {
      expression <- definition.body
      if !expression.isInstanceOf[ScBlockExpr]
      block = ScalaPsiElementFactory.createBlockFromExpr(expression)(expression.getManager)
    } expression.replace(block)

    for {
      assignment <- definition.assignment
    } assignment.delete()
  }

}
