package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocDescriptionPart, ScDocTag}

import java.util

final class ScDocCommentImpl(buffer: CharSequence,
                             override val getTokenType: IElementType)
  extends LazyParseablePsiElement(getTokenType, buffer)
    with ScDocComment {

  override def getOwner: PsiDocCommentOwner = getParent match {
    case owner: PsiDocCommentOwner if owner.getDocComment eq this => owner
    case _ => null
  }

  override def toString: String = "DocComment"

  override def processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    val owner = getOwner
    owner match {
      case typeDef: ScTemplateDefinition =>
        val members = typeDef.membersWithSynthetic.filterNot(_.is[ScPrimaryConstructor])
        val stops: Seq[Boolean] = members.map {
          case named: PsiNamedElement =>
            !processor.execute(named, state)
          case _ =>
            false
        }
        stops.exists(identity)
      case _ =>
    }

    true
  }

  //todo: implement me
  override def getTags: Array[PsiDocTag] = findTagsByName(_ => true)

  override def getDescriptionElements: Array[PsiElement] =
    descriptionElements.toArray

  override def descriptionParts: Seq[ScDocDescriptionPart] =
    descriptionElements
      .filterByType[ScDocDescriptionPart]
      .toSeq

  private def descriptionElements: Iterator[PsiElement] = {
    val beforeTags = this.getFirstChildNode.treeNextNodes.takeWhile(_.getElementType != ScalaDocElementTypes.DOC_TAG)
    beforeTags
      .filter { node =>
        val elementType = node.getElementType
        elementType != ScalaDocTokenType.DOC_COMMENT_START &&
          elementType != ScalaDocTokenType.DOC_COMMENT_END &&
          elementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
      }
      .map(_.getPsi)
  }

  override def findTagByName(name: String): PsiDocTag = {
    val tags = findTagsByName(name)
    tags.headOption.orNull
  }

  override def tags: Seq[ScDocTag] =
    this.children.filterByType[ScDocTag].toSeq

  override def findTagsByName(name: String): Array[PsiDocTag] =
    findTagsByName(_ == name)

  override def findTagsByName(filter: String => Boolean): Array[PsiDocTag] =
    this.children
      .takeWhile(_.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_END)
      .filter {
        case docTag: ScDocTag if docTag.getNode.getElementType == ScalaDocElementTypes.DOC_TAG => filter(docTag.name)
        case _ => false
      }
      .map(_.asInstanceOf[ScDocTag])
      .toArray


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
