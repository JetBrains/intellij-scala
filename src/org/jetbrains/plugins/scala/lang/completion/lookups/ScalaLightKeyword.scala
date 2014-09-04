package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * @author Alefas
 * @since 27.03.12
 */

class ScalaLightKeyword(manager: PsiManager, text: String) extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with ScalaPsiElement {
  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def getText: String = text

  def getTokenType: IElementType = {
    var lexer = new ScalaLexer
    lexer.start(text)
    lexer.getTokenType
  }

  override def copy: PsiElement = new ScalaLightKeyword(getManager, text)

  override def toString: String = "ScalaLightKeyword:" + text
}
