package org.jetbrains.plugins.scala
package codeInsight
package intention
package expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getNonStrictParentOfType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.MakeExplicitAction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class MakeImplicitConversionExplicit extends PsiElementBaseIntentionAction {

  import MakeImplicitConversionExplicit._

  override def getFamilyName: String = ScalaBundle.message("family.name.make.implicit.conversion.explicit")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findImplicitElement(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    for {
      (expression, function) <- findImplicitElement(element)

      importStatically = expression.implicitConversions(fromUnderscore = true).contains(function)
    } MakeExplicitAction.replaceWithExplicit(expression, function, importStatically)(project, editor)
}

object MakeImplicitConversionExplicit {
  private def findImplicitElement(element: PsiElement) = for {
    parent <- Option(getNonStrictParentOfType(element, classOf[ScExpression]))
    if parent.isValid

    function <- parent.implicitElement(fromUnderscore = true)
    if function.isInstanceOf[ScFunction]
  } yield (parent, function.asInstanceOf[ScFunction])

}