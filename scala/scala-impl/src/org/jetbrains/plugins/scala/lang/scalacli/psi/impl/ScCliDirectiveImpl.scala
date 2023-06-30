package org.jetbrains.plugins.scala.lang.scalacli.psi.impl

import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.ScCliDirective

final class ScCliDirectiveImpl(buffer: CharSequence, tokenType: IElementType)
  extends LazyParseablePsiElement(tokenType, buffer)
    with ScCliDirective {

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    findChildByClass[T](clazz)

  override def getTokenType: IElementType = tokenType
}