package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.project.UserDataHolderExt

import scala.collection.mutable

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

  private val key = Key.create[mutable.HashMap[String, ScalaLightKeyword]]("scala.light.keywords")

  def apply(text: String)
           (implicit project: Project): ScalaLightKeyword = {
    val manager = PsiManager.getInstance(project)
    val map = manager.getOrUpdateUserData(key, mutable.HashMap[String, ScalaLightKeyword]())
    map.getOrElseUpdate(text, new ScalaLightKeyword(manager, text))
  }
}
