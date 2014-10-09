package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class ConvertImplicitBoundsToImplicitParameter extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Convert Implicit Bounds"

  override def getText: String = "Convert view and context bounds to implicit parameters"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val paramTypeElement: ScTypeBoundsOwner = PsiTreeUtil.getParentOfType(element, classOf[ScTypeBoundsOwner], false)
    val scTypeParamOwner: ScTypeParametersOwner = PsiTreeUtil.getParentOfType(paramTypeElement, classOf[ScTypeParametersOwner], true)
    paramTypeElement != null && paramTypeElement.hasImplicitBound && !scTypeParamOwner.isInstanceOf[ScTrait]
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (element == null || !element.isValid) return
    val (function: ScMethodLike, paramOwner: ScParameterOwner, typeParamOwner: ScTypeParametersOwner) = PsiTreeUtil.getParentOfType(element, classOf[ScParameterOwner], false) match {
      case x: ScFunction => (x, x, x)
      case x: ScClass => (x.constructor.getOrElse(return), x, x)
      case _ => return
    }
    def removeImplicitBounds() {
      typeParamOwner.typeParameters.foreach(_.removeImplicitBounds())
    }
    val declaredClauses: Seq[ScParameterClause] = paramOwner.allClauses
    declaredClauses.lastOption match {
      case Some(paramClause) if paramClause.isImplicit =>
        // Already has an implicit parameter clause: delete it, add the bounds, then
        // add the parameters from the deleted clause the the new one.
        paramClause.delete()
        function.effectiveParameterClauses.lastOption match {
          case Some(implicitParamClause) if implicitParamClause.isImplicit =>
            val newClause = ScalaPsiElementFactory.createClauseFromText(implicitParamClause.getText, element.getManager)
            for (p <- paramClause.parameters) {
              val newParam = ScalaPsiElementFactory.createParameterFromText(p.getText, element.getManager)
              newClause.addParameter(newParam)
            }
            function.parameterList.addClause(newClause)
            removeImplicitBounds()
            UndoUtil.markPsiFileForUndo(function.getContainingFile)
          case _ =>
        }
      case _ =>
        function.effectiveParameterClauses.lastOption match {
          case Some(implicitParamClause) if implicitParamClause.isImplicit =>
            // for a constructor, might need to add an empty parameter section before the
            // implicit section.
            val extra = function.effectiveParameterClauses.drop(declaredClauses.size)
            for(c <- extra) {
              val newClause = ScalaPsiElementFactory.createClauseFromText(c.getText, element.getManager)
              function.parameterList.addClause(newClause)
            }
            removeImplicitBounds()
            UndoUtil.markPsiFileForUndo(function.getContainingFile)
          case _ =>
        }
    }
  }
}