package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Boolean => JBoolean}

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{LiteralTextEscaper, PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, result}

final class ScBooleanLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends expr.ScExpressionImplBase(node)
    with ScBooleanLiteral {

  override protected def innerType: result.TypeResult =
    ScLiteralType.inferType(this)

  override def getValue: JBoolean = {
    import lang.lexer.ScalaTokenTypes._
    getNode.getFirstChildNode.getElementType match {
      case `kTRUE` => JBoolean.TRUE
      case `kFALSE` => JBoolean.FALSE
    }
  }

  override def contentRange: TextRange = getTextRange

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  // TODO all the methods are not applicable

  override def isString: Boolean = false

  override def isMultiLineString: Boolean = false

  override def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner] = None

  override def isChar: Boolean = false

  override def isValidHost: Boolean = false

  override def updateText(s: String): PsiLanguageInjectionHost = null

  override def createLiteralTextEscaper(): LiteralTextEscaper[_ <: PsiLanguageInjectionHost] = null
}
