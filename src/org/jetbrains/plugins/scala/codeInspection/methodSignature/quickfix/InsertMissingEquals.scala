package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class InsertMissingEquals(functionDecl: ScFunctionDeclaration) extends LocalQuickFix {
  def getFamilyName = getName

  def getName = "Insert missing '='"

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    functionDecl.typeElement match {
      case Some(cte: ScCompoundTypeElement) =>
        val typeElem = cte.components.last
        val fileText = cte.getContainingFile.getText

        val defAndReturnType = {
          val defAndReturnTypeRange = TextRange.create(functionDecl.getTextRange.getStartOffset, typeElem.getTextRange.getEndOffset)
          defAndReturnTypeRange.substring(fileText)
        }
        val methodBody = {
          val methodBodyRange = TextRange.create(typeElem.getTextRange.getEndOffset + 1, cte.getTextRange.getEndOffset)
          methodBodyRange.substring(fileText)
        }
        val newMethod = ScalaPsiElementFactory.createMethodFromText(defAndReturnType + " = " + methodBody, functionDecl.getManager)
        functionDecl.replace(newMethod)
      case _ =>
    }
  }
}