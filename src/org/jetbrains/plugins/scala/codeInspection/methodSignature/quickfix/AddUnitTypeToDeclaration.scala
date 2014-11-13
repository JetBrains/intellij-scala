package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class AddUnitTypeToDeclaration(functionDecl: ScFunctionDeclaration)
        extends AbstractFixOnPsiElement(InspectionBundle.message("add.unit.type.to.declaration"), functionDecl) {

  def doApplyFix(project: Project) {
    val funDef = getElement
    val manager = funDef.getManager
    val colon = ScalaPsiElementFactory.createColon(manager)
    val whitespace = ScalaPsiElementFactory.createWhitespace(manager)
    val typeElem = ScalaPsiElementFactory.createTypeElementFromText("Unit", manager)
    funDef.getNode.addChild(colon.getNode)
    funDef.getNode.addChild(whitespace.getNode)
    funDef.getNode.addChild(typeElem.getNode)
  }
}
