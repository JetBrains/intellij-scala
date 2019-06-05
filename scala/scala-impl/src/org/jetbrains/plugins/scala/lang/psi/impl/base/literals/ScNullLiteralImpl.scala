package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{LiteralTextEscaper, PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.types._

final class ScNullLiteralImpl(node: ASTNode,
                              override val toString: String) extends expr.ScExpressionImplBase(node)
  with literals.ScNullLiteral {

  override protected def innerType = Right(api.Null(getProject))

  override def getValue: Null = null

  override def contentRange: TextRange = getTextRange

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  // TODO all the methods are not applicable

  override def isString: Boolean = false

  override def isMultiLineString: Boolean = false

  override def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner] = None

  override def isSymbol: Boolean = false

  override def isChar: Boolean = false

  override def isValidHost: Boolean = false

  override def updateText(s: String): PsiLanguageInjectionHost = null

  override def createLiteralTextEscaper(): LiteralTextEscaper[_ <: PsiLanguageInjectionHost] = null
}
