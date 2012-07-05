package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.psi.{PsiDocumentManager, NavigatablePsiElement, PsiElement}
import lang.psi.api.expr.{ScMethodCall, ScExpression}
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.statements.ScFunction
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import scala.Some
import lang.psi.impl.toplevel.typedef.ScObjectImpl
import lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * @author Ksenia.Sautina
 * @since 5/4/12
 */

object InlineImplicitConversionIntention {
  def familyName = "Provide implicit conversion"
}

class InlineImplicitConversionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = InlineImplicitConversionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =  {
    val expr : ScExpression = PsiTreeUtil.getParentOfType(element, classOf[ScExpression], false)
    if (expr == null) return false

    val implicitConversions = expr.getImplicitConversions(true)
    val conversionFun = implicitConversions._2.getOrElse(null)
    if (conversionFun == null) return false

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val expr : ScExpression = PsiTreeUtil.getParentOfType(element, classOf[ScExpression], false)
    if (expr == null || !expr.isValid) return

    val implicitConversions = expr.getImplicitConversions(true)
    val conversionFun = implicitConversions._2.getOrElse(null)
    if (conversionFun == null || !conversionFun.isInstanceOf[ScFunction]) return
    val secondPart = implicitConversions._4.getOrElse(Seq.empty)

    IntentionUtils.replaceWithExplicit(expr, conversionFun.asInstanceOf[ScFunction], project, editor, secondPart)
  }
}
