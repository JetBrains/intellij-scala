package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

/**
 * TODO: write a test for std lib like org.jetbrains.plugins.scala.projectHighlighting.ScalaLibraryHighlightingTest
 */
private trait MacroFinder {
  def getMacroBody(name: String): Option[String]
}

private object MacroFinderDummy extends MacroFinder {
  override def getMacroBody(name: String): Option[String] = Some("")
}

private class MacroFinderImpl(
  commentOwner: ScDocCommentOwner,
  rendered: Boolean
) extends MacroFinder {
  private val myCache = mutable.HashMap[String, String]()

  private var resolvableCommentOwners: Seq[ScDocCommentOwner] = Seq.empty
  private var init = false

  override def getMacroBody(macroName: String): Option[String] = {
    if (!init) {
      resolvableCommentOwners = findCommentOwnersWithResolvableMacroDefinitions(commentOwner)
      init = true
    }

    myCache.get(macroName) match {
      case macroValue: Some[_] =>
        return macroValue
      case None             =>
    }

    findMacroValue(macroName)
  }

  private def findMacroValue(macroName: String): Option[String] = {
    val comments: Iterator[ScDocComment] = for {
      owner       <- resolvableCommentOwners.iterator
      actualOwner <- owner.getNavigationElement.asOptionOf[ScDocCommentOwner]
      comment     <- actualOwner.docComment
    } yield comment

    val macroValues: Iterator[String] = for {
      comment <- comments
      (defineKey, defineTag) <- findDefineTags(comment)
      if defineKey.nonEmpty && defineKey == macroName
    } yield defineTagValue(comment, defineTag)

    val macroValue = macroValues.nextOption()
    macroValue.foreach { value =>
      myCache.put(macroName, value)
    }
    macroValue
  }

  private def findDefineTags(comment: ScDocComment): Seq[(String, ScDocTag)] =
    for {
      tag <- comment.getTags.toSeq
      if tag.name == MyScaladocParsing.DEFINE_TAG
      defineTag = tag.asInstanceOf[ScDocTag]
      key <- defineTagKey(defineTag)
    } yield (key, defineTag)

  private def defineTagKey(tag: ScDocTag): Option[String] = {
    val valueElement = Option(tag.getValueElement)
    valueElement.map(_.getText)
  }

  private def defineTagValue(comment: ScDocComment, tag: ScDocTag): String = {
    val macroFinder = MacroFinderDummy // TODO: for now we do not support recursive macros, only 1 level
    val generator = new ScalaDocContentGenerator(comment, macroFinder)
    generator.tagDescriptionText(tag)
  }

  private def findCommentOwnersWithResolvableMacroDefinitions(commentOwner: ScDocCommentOwner): Seq[ScDocCommentOwner] = {
    // NOTE: the order is important so using LinkedHashSet here!
    // we want the first macro definition to be used, not arbitrary macro from the hierarchy
    val result = mutable.LinkedHashSet.empty[ScDocCommentOwner]

    val queue = mutable.Queue[ScDocCommentOwner](commentOwner)

    def enqueue(el: PsiElement): Unit = el  match {
      case od: ScDocCommentOwner =>
        /**
         * Even though there should not be class inheritor cycles in valid programs, it is still possible to write them
         * by accident. For example, generating scala doc here could start an infinite cycle:
         * {{{
         * /** @define bar bar */
         * class A extends B
         *
         * /** $bar
         *  * @define foo foo */
         * class B extends A
         * }}}
         */
        if (!result.contains(od))
          queue += od
      case _ =>
    }

    var continue = true
    while (queue.nonEmpty && continue) {
      val next = queue.dequeue()
      next match {
        case clazz: ScTemplateDefinition =>
          result += clazz

          val supers = clazz.supers
          supers.foreach(enqueue)

          Option(clazz.containingClass).foreach(enqueue)
        case member: ScMember =>
          result += member

          if (member.hasModifierPropertyScala("override")){
            val fromSuper = member match {
              case named: ScNamedElement => ScalaPsiUtil.superValsSignatures(named).map(_.namedElement)
              case _ => Seq()
            }
            fromSuper.foreach(enqueue)
          }
          Option(member.containingClass).foreach(enqueue)
        case _ =>
          continue = false
      }
    }

    result.toSeq
  }
}