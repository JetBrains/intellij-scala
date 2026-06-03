package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.command.CompletionCommandKt.getCommandContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//noinspection UnstableApiUsage
package object command {
  @Nullable
  def getNonWhitespaceCommandContext(@NotNull file: PsiFile, offset: Int): PsiElement =
    getCommandContext(offset, file) match {
      case ws: PsiWhiteSpace => PsiTreeUtil.prevVisibleLeaf(ws)
      case ctx => ctx
    }

  @Nullable
  def getIdentifierOrNameIdCommandContext(@NotNull file: PsiFile, offset: Int): PsiElement = {
    val element = getNonWhitespaceCommandContext(file, offset)
    if (element == null) return null

    val elementType = element.elementType
    elementType match {
      case ScalaTokenTypes.tIDENTIFIER => element
      case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE =>
        element.getParent match {
          case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(td: ScTypeDefinition)) => td.nameId
          case (_: ScBlockExpr) & Parent(fun: ScFunctionDefinition) => fun.nameId
          case _ => null
        }
      case ScalaTokenTypes.tRSQBRACKET =>
        element.getParent match {
          case (_: ScTypeParamClause) & Parent(named: ScNamedElement) if named.is[ScFunction, ScTypeDefinition] =>
            named.nameId
          case _ => null
        }
      case ScalaTokenTypes.tRPARENTHESIS =>
        element.getParent match {
          case (_: ScParameterClause) & Parent(params: ScParameters) =>
            params.getParent match {
              case fn: ScFunction => fn.nameId
              case (_: ScPrimaryConstructor) & Parent(td: ScTypeDefinition) => td.nameId
              case _ => null
            }
          case _ => null
        }
      case _ => null
    }
  }
}
