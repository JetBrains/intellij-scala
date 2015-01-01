package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class InsertReturnTypeAndEquals(functionDef: ScFunctionDefinition)
        extends AbstractFixOnPsiElement(InspectionBundle.message("insert.return.type.and.equals"), functionDef) {

  def doApplyFix(project: Project): Unit = {
    val funDef = getElement
    funDef.removeAssignment()
    funDef.removeExplicitType()
    val manager = funDef.getManager
    val fakeDecl = ScalaPsiElementFactory.createDeclaration("x", "Unit", isVariable = false, null, manager)
    val colon = fakeDecl.findFirstChildByType(ScalaTokenTypes.tCOLON)
    val assign = fakeDecl.findFirstChildByType(ScalaTokenTypes.tASSIGN)
    val body = funDef.body.get
    funDef.addRangeAfter(colon, assign, body.getPrevSiblingNotWhitespace)
  }
}
