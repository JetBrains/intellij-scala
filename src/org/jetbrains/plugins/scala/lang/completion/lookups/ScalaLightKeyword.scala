package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * @author Alefas
 * @since 27.03.12
 */

class ScalaLightKeyword private (manager: PsiManager, text: String)
  extends LightElement(manager, ScalaLanguage.INSTANCE) with ScalaPsiElement {
  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def getText: String = text

  def getTokenType: IElementType = {
    val lexer = new ScalaLexer
    lexer.start(text)
    lexer.getTokenType
  }

  override def copy: PsiElement = new ScalaLightKeyword(getManager, text)

  override def toString: String = "ScalaLightKeyword:" + text
}

object ScalaLightKeyword {
  private val keywords = ContainerUtil.newConcurrentMap[(PsiManager, String), ScalaLightKeyword]()

  def apply(manager: PsiManager, text: String): ScalaLightKeyword = {
    var res = keywords.get((manager, text))
    if (res != null && res.isValid) return res
    res = new ScalaLightKeyword(manager, text)
    keywords.put((manager, text), res)
    res
  }
}
