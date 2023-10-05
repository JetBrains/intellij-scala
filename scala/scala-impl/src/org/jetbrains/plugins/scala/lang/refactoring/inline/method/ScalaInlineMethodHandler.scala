package org.jetbrains.plugins.scala.lang.refactoring.inline.method

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Condition, NlsContexts}
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.HelpID
import com.intellij.util.FilteredQuery
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineActionHandler
import org.jetbrains.plugins.scala.lang.refactoring.inline.method.ScalaInlineMethodHandler.hasNoCallUsages

final class ScalaInlineMethodHandler extends ScalaInlineActionHandler {
  override protected val helpId: String = ScalaInlineMethodHandler.HelpId
  override protected val refactoringName: String = ScalaInlineMethodHandler.RefactoringName

  override protected def canInlineScalaElement(element: ScalaPsiElement): Boolean = element.is[ScFunctionDefinition]

  override protected def inlineScalaElement(element: ScalaPsiElement)(implicit project: Project, editor: Editor): Unit = element match {
    case funDef: ScFunctionDefinition =>
      if (funDef.recursiveReferences.nonEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.recursive.function"))
      else if (funDef.paramClauses.clauses.size > 1)
        showErrorHint(ScalaBundle.message("cannot.inline.function.multiple.clauses"))
      else if (funDef.paramClauses.clauses.exists(_.isImplicit))
        showErrorHint(ScalaBundle.message("cannot.inline.function.implicit.parameters"))
      else if (funDef.parameters.exists(_.isVarArgs))
        showErrorHint(ScalaBundle.message("cannot.inline.function.varargs"))
      else if (funDef.isSpecial)
        showErrorHint(ScalaBundle.message("cannot.inline.special.function"))
      else if (funDef.typeParameters.nonEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.generic.function"))
      else if (funDef.parameters.exists(isFunctionalType))
        showErrorHint(ScalaBundle.message("cannot.inline.function.functional.parameters"))
      else if (funDef.parameters.nonEmpty && hasNoCallUsages(funDef))
        showErrorHint(ScalaBundle.message("cannot.inline.not.method.call"))
      else if (funDef.body.isDefined) {
        if (validateReferences(funDef)) {
          val dialog = new ScalaInlineMethodDialog(funDef)
          showDialog(dialog)
        }
      }
    case _ =>
  }
}

object ScalaInlineMethodHandler {
  val HelpId: String = HelpID.INLINE_METHOD

  @NlsContexts.DialogTitle
  val RefactoringName: String = ScalaBundle.message("inline.method.title")

  private def hasNoCallUsages(fun: ScFunctionDefinition): Boolean = {
    //we already know that all usages are in the same class
    val scope = new LocalSearchScope(fun.containingClass.toOption.getOrElse(fun.getContainingFile))

    val allReferences = ReferencesSearch.search(fun, scope)
    val notCall: Condition[PsiReference] = ref => !ref.getElement.getParent.is[ScMethodCall]
    val noCallUsages = new FilteredQuery[PsiReference](allReferences, notCall)
    noCallUsages.findFirst() != null
  }
}
