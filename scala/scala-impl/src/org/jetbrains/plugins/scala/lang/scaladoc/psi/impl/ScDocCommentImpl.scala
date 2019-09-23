package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import java.util

import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
final class ScDocCommentImpl(buffer: CharSequence,
                             override val getTokenType: IElementType)
  extends LazyParseablePsiElement(getTokenType, buffer)
    with ScDocComment {

  override def version: Int = {
    val firstLineIsEmpty = getNode.getChildren(null).lift(2).exists(
      _.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)

    if (firstLineIsEmpty) 1 else 2
  }

  override def getOwner: PsiDocCommentOwner = getParent match {
    case owner: PsiDocCommentOwner if owner.getDocComment eq this => owner
    case _ => null
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super.processDeclarations(processor, state, lastParent, place) && !Option(getOwner).exists {
      case owner: ScClass =>
        owner.membersWithSynthetic.exists {
          case named: PsiNamedElement => !processor.execute(named, state)
          case _ => false
        }
      case _ => false
    }
  }

  override def toString: String = "DocComment"

  //todo: implement me
  override def getTags: Array[PsiDocTag] = findTagsByName(_ => true)

  override def getDescriptionElements: Array[PsiElement] = PsiElement.EMPTY_ARRAY

  override def findTagByName(name: String): PsiDocTag = if (findTagsByName(name).length > 0) findTagsByName(name)(0) else null

  override def findTagsByName(name: String): Array[PsiDocTag] = findTagsByName(a => a == name)

  override def findTagsByName(filter: String => Boolean): Array[PsiDocTag] = {
    var currentChild = getFirstChild
    val answer = mutable.ArrayBuilder.make[PsiDocTag]()

    while (currentChild != null && currentChild.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_END) {
      currentChild match {
        case docTag: ScDocTag if docTag.getNode.getElementType == ScalaDocElementTypes.DOC_TAG &&
          filter(docTag.name) => answer += currentChild.asInstanceOf[PsiDocTag]
        case _ =>

      }
      currentChild = currentChild.getNextSibling
    }

    answer.result()
  }


  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result: util.List[T] = new util.ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitDocComment(this)
  }
}