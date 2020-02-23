package org.jetbrains.plugins.scala
package codeInsight
package intention
package expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getNonStrictParentOfType
import org.jetbrains.plugins.scala.actions.MakeExplicitAction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * @author Ksenia.Sautina
  * @since 5/4/12
  */
class MakeImplicitConversionExplicit extends PsiElementBaseIntentionAction {

  import MakeImplicitConversionExplicit._

  override def getFamilyName: String = FamilyName

  override def getText: String = FamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findImplicitElement(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    for {
      (expression, function) <- findImplicitElement(element)

      importStatically = expression.implicitConversions(fromUnderscore = true).contains(function)
    } MakeExplicitAction.replaceWithExplicit(expression, function, importStatically)(project, editor)
}

object MakeImplicitConversionExplicit {

  val FamilyName = "Make implicit conversion explicit"

  private def findImplicitElement(element: PsiElement) = for {
    parent <- Option(getNonStrictParentOfType(element, classOf[ScExpression]))
    if parent.isValid

    function <- parent.implicitElement(fromUnderscore = true)
    if function.isInstanceOf[ScFunction]
  } yield (parent, function.asInstanceOf[ScFunction])

}