package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result, TextResult}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.extensions.{&, OptionExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.annotation.tailrec

final class ScalaImplicitClassExtendsAnyValOptionalTextMacro extends ScalaMacro {

  override def getNameShort: String = "implicitValueExtendsValueClassOptionalText"

  override def getPresentableName: String = "implicitValueExtendsValueClassOptionalText"

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val element = context.getPsiElementAtStartOffset match {
      case (_: LeafPsiElement) & Parent(ref: ScReference) => ref // expected to be an injected synthetic reference (for completion)
      case el =>
        if (el.elementType == ScalaTokenTypes.kIMPLICIT)
          Option(el).safeMap(_.getParent).safeMap(_.getParent).orNull //edge case when caret is located in empty worksheet without any whitespaces
        else
          el
    }

    if (element == null)
      new TextResult("")
    else {
      val parent = element.getParent
      val extendsListText = if (isStaticallyAccessible(parent)) " extends AnyVal " else ""
      new TextResult(extendsListText)
    }
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = {
    calculateResult(params, context)
  }

  @tailrec
  private def isStaticallyAccessible(element: PsiElement): Boolean =
    element match {
      case _: ScalaFile | _: ScPackaging =>
        true
      case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(obj: ScObject)) =>
        isStaticallyAccessible(obj.getParent)
      case _ =>
        false
    }
}
