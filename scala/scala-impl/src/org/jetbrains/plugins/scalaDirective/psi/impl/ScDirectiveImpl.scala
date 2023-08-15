package org.jetbrains.plugins.scalaDirective.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

final class ScDirectiveImpl(buffer: CharSequence, tokenType: IElementType)
  extends LazyParseablePsiElement(tokenType, buffer)
    with ScDirective {

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    findChildByClass[T](clazz)

  override def getTokenType: IElementType = tokenType

  override def key: Option[PsiElement] = findFirstChildByType(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY)

  override def value: Option[PsiElement] = findFirstChildByType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)
}
