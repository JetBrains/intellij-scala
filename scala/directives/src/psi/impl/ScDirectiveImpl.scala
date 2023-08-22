package org.jetbrains.plugins.scalaDirective
package psi.impl

import lang.lexer.ScalaDirectiveTokenTypes

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
//import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import psi.api.ScDirective

final class ScDirectiveImpl(buffer: CharSequence, tokenType: IElementType)
  extends LazyParseablePsiElement(tokenType, buffer)
    with ScDirective {

//  override protected def findChildrenByClassScala[T >: Null <: /*Scala*/PsiElement](clazz: Class[T]): Array[T] =
//    findChildrenByClass[T](clazz)
//
//  override protected def findChildByClassScala[T >: Null <: /*Scala*/PsiElement](clazz: Class[T]): T =
//    findChildByClass[T](clazz)

  override def getTokenType: IElementType = tokenType

  override def key: Option[PsiElement] = Option(findChildByType(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY)).map(_.getPsi)

  override def value: Option[PsiElement] = Option(findChildByType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)).map(_.getPsi)
}
