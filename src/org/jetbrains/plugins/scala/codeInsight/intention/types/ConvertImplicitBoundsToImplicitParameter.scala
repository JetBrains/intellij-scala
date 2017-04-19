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
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createClauseFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper

import scala.collection.JavaConverters._

class ConvertImplicitBoundsToImplicitParameter extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Convert Implicit Bounds"

  override def getText: String = "Convert view and context bounds to implicit parameters"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    canBeConverted(element)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val addedParams = doConversion(element)
    runRenamingTemplate(addedParams)
  }
}

object ConvertImplicitBoundsToImplicitParameter {

  def canBeConverted(element: PsiElement): Boolean = {
    val paramTypeElement: ScTypeBoundsOwner = PsiTreeUtil.getParentOfType(element, classOf[ScTypeBoundsOwner], false)
    val scTypeParamOwner: ScTypeParametersOwner = PsiTreeUtil.getParentOfType(paramTypeElement, classOf[ScTypeParametersOwner], true)
    paramTypeElement != null && paramTypeElement.hasImplicitBound && !scTypeParamOwner.isInstanceOf[ScTrait]
  }
  
  def doConversion(element: PsiElement): Seq[ScParameter] = {
    if (element == null || !element.isValid) return Seq.empty
    val (function: ScMethodLike, paramOwner: ScParameterOwner, typeParamOwner: ScTypeParametersOwner) = 
      PsiTreeUtil.getParentOfType(element, classOf[ScParameterOwner], false) match {
        case x: ScFunction => (x, x, x)
        case x: ScClass => (x.constructor.getOrElse(return Seq.empty), x, x)
        case _ => return Seq.empty
      }

    import function.projectContext

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
            val newClause = createClauseFromText(implicitParamClause.getText)
            val addedParametersCount = newClause.parameters.size
            for (p <- paramClause.parameters) {
              val newParam = createParameterFromText(p.getText)
              newClause.addParameter(newParam)
            }
            val addedClause = function.parameterList.addClause(newClause).clauses.last
            removeImplicitBounds()
            UndoUtil.markPsiFileForUndo(function.getContainingFile)
            addedClause.parameters.take(addedParametersCount)
          case _ => Seq.empty
        }
      case _ =>
        function.effectiveParameterClauses.lastOption match {
          case Some(implicitParamClause) if implicitParamClause.isImplicit =>
            // for a constructor, might need to add an empty parameter section before the
            // implicit section.
            val extra = function.effectiveParameterClauses.drop(declaredClauses.size).headOption
            var result: Seq[ScParameter] = Seq.empty
            for(c <- extra) {
              val newClause = createClauseFromText(c.getText)
              val addedParametersCount = c.parameters.size
              val addedClause = function.parameterList.addClause(newClause).clauses.last
              result = addedClause.parameters.take(addedParametersCount)
            }
            removeImplicitBounds()
            UndoUtil.markPsiFileForUndo(function.getContainingFile)
            result
          case _ => Seq.empty
        }
    }
  }

  def runRenamingTemplate(params: Seq[ScParameter]): Unit = {
    if (params.isEmpty) return

    val parent = PsiTreeUtil.findCommonParent(params.asJava)
    val helper = new InplaceRenameHelper(parent)
    params.foreach(p => helper.addGroup(p, Seq.empty, Seq.empty))
    helper.startRenaming()
  }
}