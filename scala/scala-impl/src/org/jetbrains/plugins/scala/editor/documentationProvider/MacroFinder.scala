package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.MacroFinderImpl.DefineTagContentTokens
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

/**
 * TODO: $seqInfo is not found in scala.collection.immutable.Seq
 *  write a test for std lib like org.jetbrains.plugins.scala.projectHighlighting.ScalaLibraryHighlightingTest
 */
private trait MacroFinder {
  def getMacroBody(name: String): Option[String]
}

private object MacroFinderDummy extends MacroFinder {
  override def getMacroBody(name: String): Option[String] = None
}

private class MacroFinderImpl(
  commentOwner: ScDocCommentOwner,
  renderDefineTagChildElement: PsiElement => String
) extends MacroFinder {
  private val myCache = mutable.HashMap[String, String]()

  private val processingQueue = mutable.Queue.apply[ScDocCommentOwner]()
  private var init = false

  override def getMacroBody(macroName: String): Option[String] = {
    if (!init) {
      fillQueue()
      init = true
    }

    myCache.get(macroName) match {
      case macroValue: Some[_] => return macroValue
      case None             =>
    }

    findMacroValue(macroName)
  }

  private def findMacroValue(macroName: String): Option[String] = {
    val commentOwners   = dequeueIterator(processingQueue)
    val comments = for {
      owner   <- commentOwners
      comment <- owner.docComment
    } yield comment

    val defineTags = comments.flatMap(findDefineTags)
    defineTags.find(_._1 == macroName).map(_._2)
  }

  private def findDefineTags(comment: ScDocComment): Seq[(String, String)] =
    for {
      tag <- comment.getTags
      if tag.name == MyScaladocParsing.DEFINE_TAG
    } yield getDefineTagNameAndValue(tag.asInstanceOf[ScDocTag])

  private def getDefineTagNameAndValue(tag: ScDocTag): (String, String) = {
    val valueElement = Option(tag.getValueElement)
    val tagText = valueElement.map(_.getText).mkString

    val tagValue = defineTagContentChildren(tag).map(renderDefineTagChildElement).mkString(" ").trim
    val result = (tagText, tagValue)
    if (tagText.nonEmpty)
      myCache += result
    result
  }

  private def defineTagContentChildren(tag: ScDocTag): Array[PsiElement] =
    tag.getNode.getChildren(DefineTagContentTokens).map(_.getPsi)

  private def fillQueue(): Unit = {
    def fillInner(from: Iterable[ScDocCommentOwner]): Unit = {
      if (from.isEmpty) return
      val tc = mutable.ArrayBuffer.apply[ScDocCommentOwner]()

      from.foreach {
        case clazz: ScTemplateDefinition =>
          processingQueue enqueue clazz

          clazz.supers.foreach {
            case cz: ScDocCommentOwner => tc += cz
            case _ =>
          }
        case member: ScMember if member.hasModifierProperty("override") =>
          processingQueue enqueue member

          member match {
            case named: ScNamedElement =>
              ScalaPsiUtil.superValsSignatures(named).map(_.namedElement).foreach {
                case od: ScDocCommentOwner => tc += od
                case _ =>
              }
            case _ =>
          }

          member.containingClass match {
            case od: ScDocCommentOwner => tc += od
            case _ =>
          }
        case member: ScMember if member.containingClass != null =>
          processingQueue enqueue member

          member.containingClass match {
            case od: ScDocCommentOwner => tc += od
            case _ =>
          }
        case _ => return
      }

      fillInner(tc)
    }

    fillInner(Option(commentOwner))
  }

  private def dequeueIterator[T](queue: mutable.Queue[T]): Iterator[T] = new Iterator[T] {
    override def hasNext: Boolean = queue.nonEmpty
    override def next(): T = queue.dequeue()
  }
}

object MacroFinderImpl {

  private val DefineTagContentTokens = ScalaDocTokenType.ALL_SCALADOC_SYNTAX_ELEMENTS ++ ScalaDocTokenType.DOC_COMMENT_DATA
}