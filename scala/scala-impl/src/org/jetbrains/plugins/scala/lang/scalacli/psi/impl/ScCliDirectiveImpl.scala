package org.jetbrains.plugins.scala.lang.scalacli.psi.impl

import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.ScCliDirective
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.inner.{ScCliDirectiveCommand, ScCliDirectiveKey, ScCliDirectiveValue}

final class ScCliDirectiveImpl(buffer: CharSequence, tokenType: IElementType)
  extends LazyParseablePsiElement(tokenType, buffer)
    with ScCliDirective {

  override def command: Option[ScCliDirectiveCommand] =
    findChildrenByClassScala(classOf[ScCliDirectiveCommand]).headOption

  override def key: Option[ScCliDirectiveKey] =
    findChildrenByClassScala(classOf[ScCliDirectiveKey]).headOption

  override def values: Seq[ScCliDirectiveValue] =
    findChildrenByClassScala(classOf[ScCliDirectiveValue]).toSeq

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def getTokenType: IElementType = tokenType
}