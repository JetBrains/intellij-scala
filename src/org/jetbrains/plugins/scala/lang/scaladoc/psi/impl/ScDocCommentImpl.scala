package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.tree.IElementType
import java.lang.String
import lang.psi.ScalaPsiElement
import parser.ScalaDocElementTypes
import com.intellij.util.ReflectionCache
import java.util.{List, ArrayList}
import lang.psi.api.ScalaElementVisitor
import com.intellij.psi.{PsiElementVisitor, PsiDocCommentOwner, PsiElement}
import lexer.ScalaDocTokenType
import api.{ScDocTag, ScDocComment}
import collection.mutable.ArrayBuilder
import extensions.toPsiNamedElementExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocCommentImpl(text: CharSequence) extends LazyParseablePsiElement(ScalaDocElementTypes.SCALA_DOC_COMMENT, text) with ScDocComment {
  def version: Int = {
    val firstLineIsEmpty = getNode.getChildren(null).lift(2).exists(
      _.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)

    if (firstLineIsEmpty) 1 else 2
  }

  def getOwner: PsiDocCommentOwner = getParent match {
    case owner: PsiDocCommentOwner if owner.getDocComment eq this => owner
    case _ => null
  }
  
  def getTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT

  override def toString: String = "DocComment"

  //todo: implement me
  def getTags: Array[PsiDocTag] = findTagsByName(_ => true)

  def getDescriptionElements: Array[PsiElement] = null

  def findTagByName(name: String): PsiDocTag = if (findTagsByName(name).length > 0) findTagsByName(name)(0) else null

  def findTagsByName(name: String): Array[PsiDocTag] = findTagsByName(a => a == name)
  
  def findTagsByName(filter: String => Boolean): Array[PsiDocTag] = {
    var currentChild = getFirstChild
    val answer = ArrayBuilder.make[PsiDocTag]()

    while (currentChild != null && currentChild.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_END) {
      currentChild match {
        case docTag: ScDocTag if docTag.getNode.getElementType == ScalaDocElementTypes.DOC_TAG  &&
          filter(docTag.name) => answer += currentChild.asInstanceOf[PsiDocTag]
        case _ =>

      }
      currentChild = currentChild.getNextSibling
    }

    answer.result()
  }


  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result: List[T] = new ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitDocComment(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }
}