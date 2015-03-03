package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import java.util

import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

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

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super.processDeclarations(processor, state, lastParent, place) && !Option(getOwner).exists {
      case owner: ScClass =>
        owner.members.exists {
          case named: PsiNamedElement => !processor.execute(named, state)
          case _ => false
        }
      case _ => false
    }
  }

  def getTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT

  override def toString: String = "DocComment"

  //todo: implement me
  def getTags: Array[PsiDocTag] = findTagsByName(_ => true)

  def getDescriptionElements: Array[PsiElement] = PsiElement.EMPTY_ARRAY

  def findTagByName(name: String): PsiDocTag = if (findTagsByName(name).length > 0) findTagsByName(name)(0) else null

  def findTagsByName(name: String): Array[PsiDocTag] = findTagsByName(a => a == name)
  
  def findTagsByName(filter: String => Boolean): Array[PsiDocTag] = {
    var currentChild = getFirstChild
    val answer = mutable.ArrayBuilder.make[PsiDocTag]()

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
    val result: util.List[T] = new util.ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) return cur.asInstanceOf[T]
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