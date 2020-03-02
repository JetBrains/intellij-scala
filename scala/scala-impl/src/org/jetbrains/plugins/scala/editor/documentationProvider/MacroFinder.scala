package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable


private trait MacroFinder {
  def getMacroBody(name: String): Option[String]
}

private class MacroFinderDummy extends MacroFinder {
  override def getMacroBody(name: String): Option[String] = None
}

private class MacroFinderImpl(comment: ScDocComment, handler: PsiElement => String) extends MacroFinder {
  private val myCache = mutable.HashMap[String, String]()
  private var lastProcessedComment: Option[PsiDocComment] = None

  private val processingQueue = mutable.Queue.apply[ScDocCommentOwner]()
  private var init = false

  override def getMacroBody(name: String): Option[String] = {
    if (!init) fillQueue()
    if (myCache contains name) return myCache get name

    var commentToProcess = selectComment2()

    while (commentToProcess.isDefined) {
      for {
        comment <- commentToProcess
        tag <- comment.getTags
        if tag.getName == MyScaladocParsing.DEFINE_TAG
        (tagName, tagValue) = tagNameAndValue(tag.asInstanceOf[ScDocTag])
        if tagName == name
      } return Option(tagValue)

      lastProcessedComment = commentToProcess
      commentToProcess = selectComment2()
    }

    None
  }

  private def tagNameAndValue(tag: ScDocTag): (String, String) = {
    val valueElement = Option(tag.getValueElement)
    val tagText = valueElement.map(_.getText).mkString
    val tagValue = tag.getAllText(handler).trim
    val result = (tagText, tagValue)
    if (tagText.nonEmpty)
      myCache += result
    result
  }

  private def fillQueue(): Unit = {
    def fillInner(from: Iterable[ScDocCommentOwner]): Unit = {
      if (from.isEmpty) return
      val tc = mutable.ArrayBuffer.apply[ScDocCommentOwner]()

      from foreach {
        case clazz: ScTemplateDefinition =>
          processingQueue enqueue clazz

          clazz.supers foreach {
            case cz: ScDocCommentOwner => tc += cz
            case _ =>
          }
        case member: ScMember if member.hasModifierProperty("override") =>
          processingQueue enqueue member

          member match {
            case named: ScNamedElement =>
              ScalaPsiUtil.superValsSignatures(named) map (sig => sig.namedElement) foreach {
                case od: ScDocCommentOwner => tc += od
                case _ =>
              }
            case _ =>
          }

          member.containingClass match {
            case od: ScDocCommentOwner => tc += od
            case _ =>
          }
        case member: ScMember if member.getContainingClass != null =>
          processingQueue enqueue member

          member.containingClass match {
            case od: ScDocCommentOwner => tc += od
            case _ =>
          }
        case _ => return
      }

      fillInner(tc)
    }

    init = true
    comment.getOwner match {
      case od: ScDocCommentOwner => fillInner(Option(od))
      case _ =>
    }
  }

  private def selectComment2(): Option[ScDocComment] = {
    while (processingQueue.nonEmpty) {
      val next = processingQueue.dequeue()

      if (next.docComment.isDefined) return next.docComment
    }

    None
  }
}