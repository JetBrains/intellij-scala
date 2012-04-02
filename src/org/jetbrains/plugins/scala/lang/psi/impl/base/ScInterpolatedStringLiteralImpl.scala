package org.jetbrains.plugins.scala
package lang.psi.impl.base

import com.intellij.lang.ASTNode
import lang.psi.api.base.ScInterpolatedStringLiteral
import lang.psi.impl.ScalaPsiManager
import lang.psi.types.{Nothing, ScType}
import lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{ResolveResult, PsiElement, PsiReference}
import lang.resolve.{ScalaResolveResult, ResolvableReferenceElement}

/**
 * User: Dmitry Naydanov
 * Date: 3/17/12
 */

class ScInterpolatedStringLiteralImpl(node: ASTNode) extends ScLiteralImpl(node) with ScInterpolatedStringLiteral {
  def getType: InterpolatedStringType.StringType = node.getFirstChildNode.getText match {
    case "s" => InterpolatedStringType.STANDART
    case "f" => InterpolatedStringType.FORMAT
    case "id" => InterpolatedStringType.PATTERN
    case _ => null
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val str = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.String")
    if (str != null) Success(ScType.designator(str), Some(this)) else Failure("Cannot find java.lang.String", Some(this))
  }
}
