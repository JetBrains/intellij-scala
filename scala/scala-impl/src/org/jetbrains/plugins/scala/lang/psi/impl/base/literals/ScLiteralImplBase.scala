package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{LiteralTextEscaper, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, result}

abstract class ScLiteralImplBase(node: ASTNode,
                                 override val toString: String)
  extends expr.ScExpressionImplBase(node)
    with ScLiteral {

  override protected final def innerType: result.TypeResult =
    ScLiteralType.inferType(this)

  override protected final def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  override def contentRange: TextRange = getTextRange

  // TODO all the methods are not applicable

  override final def isString: Boolean = false

  override final def isMultiLineString: Boolean = false

  override final def isValidHost: Boolean = false

  override final def updateText(s: String): PsiLanguageInjectionHost = null

  override final def createLiteralTextEscaper(): LiteralTextEscaper[_ <: PsiLanguageInjectionHost] = null
}
