package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMethodFromText

class InsertMissingEquals(functionDecl: ScFunctionDeclaration)
  extends AbstractFixOnPsiElement("Insert missing '='", functionDecl) {

  override protected def doApplyFix(funDef: ScFunctionDeclaration)
                                   (implicit project: Project): Unit = {
    funDef.returnTypeElement match {
      case Some(ScCompoundTypeElement(types, Some(refinement))) if types.nonEmpty =>
        val lastTypeInDecl = types.last.getTextRange.getEndOffset - funDef.getTextRange.getStartOffset
        val defAndReturnType = funDef.getText.substring(0, lastTypeInDecl)
        val methodBody = refinement.getText
        val newMethod = createMethodFromText(defAndReturnType + " = " + methodBody)
        funDef.replace(newMethod)
      case _ =>
    }
  }
}