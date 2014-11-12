package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class AddUnitTypeToDeclaration(functionDecl: ScFunctionDeclaration)
        extends AbstractFix(InspectionBundle.message("add.unit.type.to.declaration"), functionDecl) {

  def doApplyFix(project: Project) {
    val manager = functionDecl.getManager
    val colon = ScalaPsiElementFactory.createColon(manager)
    val whitespace = ScalaPsiElementFactory.createWhitespace(manager)
    val typeElem = ScalaPsiElementFactory.createTypeElementFromText("Unit", manager)
    functionDecl.getNode.addChild(colon.getNode)
    functionDecl.getNode.addChild(whitespace.getNode)
    functionDecl.getNode.addChild(typeElem.getNode)
  }
}
