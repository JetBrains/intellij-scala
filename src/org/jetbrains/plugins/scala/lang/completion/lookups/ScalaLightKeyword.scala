package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.openapi.util.Key
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

import scala.collection.mutable

/**
 * @author Alefas
 * @since 27.03.12
 */

class ScalaLightKeyword private (manager: PsiManager, text: String)
  extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with ScalaPsiElement {
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
  private val key = Key.create[mutable.HashMap[String, ScalaLightKeyword]]("scala.light.keywords")

  def apply(manager: PsiManager, text: String): ScalaLightKeyword = {
    val map = Option(manager.getUserData(key)).getOrElse {
      val newMap = mutable.HashMap[String, ScalaLightKeyword]()
      manager.putUserData(key, newMap)
      newMap
    }
    map.getOrElseUpdate(text, new ScalaLightKeyword(manager, text))
  }
}
