package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class InsertMissingEquals(functionDecl: ScFunctionDeclaration) extends AbstractFix("Insert missing '='", functionDecl) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    functionDecl.typeElement match {
      case Some(ScCompoundTypeElement(types, Some(refinement))) if types.nonEmpty =>
        val lastTypeInDecl = types.last.getTextRange.getEndOffset - functionDecl.getTextRange.getStartOffset
        val defAndReturnType = functionDecl.getText.substring(0, lastTypeInDecl)
        val methodBody = refinement.getText
        val newMethod = ScalaPsiElementFactory.createMethodFromText(defAndReturnType + " = " + methodBody, functionDecl.getManager)
        functionDecl.replace(newMethod)
      case _ =>
    }
  }
}