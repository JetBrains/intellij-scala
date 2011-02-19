package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import api.ScDocComment
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.tree.IElementType
import java.lang.String
import lang.psi.ScalaPsiElement
import parser.ScalaDocElementTypes
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import com.intellij.util.ReflectionCache
import java.util.{List, ArrayList}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocCommentImpl(text: CharSequence) extends LazyParseablePsiElement(ScalaDocElementTypes.SCALA_DOC_COMMENT, text) with ScDocComment {
  def getOwner: PsiDocCommentOwner = getParent match {
    case owner: PsiDocCommentOwner if owner.getDocComment eq this => owner
    case _ => null
  }
  
  def getTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT

  override def toString: String = "DocComment"

  //todo: implement me
  def getTags: Array[PsiDocTag] = null

  def getDescriptionElements: Array[PsiElement] = null

  def findTagByName(name: String): PsiDocTag = null

  def findTagsByName(name: String): Array[PsiDocTag] = null

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result: List[T] = new ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    return result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    return null
  }
}