package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext

package object quickfix {

  import ScalaPsiElementFactory.{createBlockFromExpr, createDeclaration}
  import ScalaTokenTypes.{tASSIGN, tCOLON}

  def removeTypeElement(function: ScFunction): Unit = for {
    colon <- findChild(function, ScalaTokenTypes.tCOLON)
    typeElement <- function.returnTypeElement
  } function.deleteChildRange(colon, typeElement)

  def removeAssignment(definition: ScFunctionDefinition)
                      (implicit context: ProjectContext): Unit = {
    for {
      expression <- definition.body
      if !expression.isInstanceOf[ScBlockExpr]
      block = createBlockFromExpr(expression)
    } expression.replace(block)

    for {
      assignment <- definition.assignment
    } assignment.delete()
  }

  def addUnitTypeElement(definition: ScFunctionDefinition)
                        (implicit context: ProjectContext): Unit = {
    val declaration = createDeclaration("x", "Unit", isVariable = false, null)
    for {
      colon <- findChild(declaration)
      assignment <- findChild(declaration, tASSIGN)

      expression <- definition.body
    } definition.addRangeAfter(colon, assignment, expression.getPrevSiblingNotWhitespace)
  }

  private[this] def findChild(element: PsiElement,
                              elementType: IElementType = tCOLON) =
    element.children.find(_.getNode.getElementType == elementType)
}
