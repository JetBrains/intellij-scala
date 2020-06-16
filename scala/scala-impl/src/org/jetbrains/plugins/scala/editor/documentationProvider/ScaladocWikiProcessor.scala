package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

// TODO: add links to @see tag: SCL-9520
// TODO: remove maximum common indentation from code examples {{{ }}} not to shift it far to the right
private object ScaladocWikiProcessor {

  final case class WikiProcessorResult(commentText: String, sections: Seq[Section])
  final case class Section(title: String, content: String)

  def replaceWikiWithTags(comment: ScDocComment): WikiProcessorResult = {
    val macroFinder = new MacroFinderImpl(comment, { element =>
      val a = getWikiTextRepresentation(element, new MacroFinderDummy)
      a._1.result()
    })

    val (commentBody, tagsPart, sections) = getWikiTextRepresentation(comment, macroFinder)
    if (tagsPart.nonEmpty) {
      commentBody.append(tagsPart)
    }
    commentBody.append("*/")
    WikiProcessorResult(commentBody.toString, sections)
  }

  private object CustomSectionTag {
    def unapply(tagName: String): Option[String] = tagName match {
      case MyScaladocParsing.TODO_TAG    => Some("Todo")
      case MyScaladocParsing.NOTE_TAG    => Some("Note")
      case MyScaladocParsing.EXAMPLE_TAG => Some("Example")
      case MyScaladocParsing.SEE_TAG     => Some("See also")
      case _                             => None
    }
  }

  private def getWikiTextRepresentation(
    comment: PsiElement,
    macroFinder: MacroFinder
  ): (mutable.StringBuilder, mutable.StringBuilder, Seq[Section]) = {

    val commentBody = StringBuilder.newBuilder
    val tagsPart = StringBuilder.newBuilder
    val sections = mutable.ArrayBuffer.empty[Section]

    def visitTagElement(tagElement: ScDocTag): Unit =
      tagElement.name match {
        case CustomSectionTag(sectionName)   =>
          val sectionBody = StringBuilder.newBuilder
          tagElement.children.foreach(visitElement(_, sectionBody, processingCustomTag = true))
          sections += Section(sectionName, sectionBody.toString.trim)
        case MyScaladocParsing.INHERITDOC_TAG =>
          tagElement.children.foreach(visitElement(_, commentBody))
        case _ =>
          tagElement.children.foreach(visitElement(_, tagsPart))
      }

    def visitElement(
      element: PsiElement,
      result: StringBuilder,
      processingCustomTag: Boolean = false
    ): Unit =
      element match {
        case _ if element.getFirstChild == null =>
          visitLeafElement(element, macroFinder, processingCustomTag, result)
        case tagElement: ScDocTag =>
          visitTagElement(tagElement)
        case _ =>
          element.children.foreach(visitElement(_, result, processingCustomTag))
      }

    visitElement(comment, commentBody)
    (commentBody, tagsPart, sections)
  }

  private def visitLeafElement(
    element: PsiElement,
    macroFinder: MacroFinder,
    processingCustomTag: Boolean,
    result: mutable.StringBuilder
  ): Unit = {
    val elementText = element.getText
    element.getNode.getElementType match {
      case ScalaDocTokenType.DOC_TAG_NAME =>
        elementText match {
          case MyScaladocParsing.TYPE_PARAM_TAG =>
            result.append("@param ")
          case CustomSectionTag(_) => // handled
          case MyScaladocParsing.INHERITDOC_TAG =>
            val commentOwner = element.getParent.getParent.getParent
            val superComment = commentOwner match {
              case fun: ScFunction             => fun.superMethod.map(_.getDocComment).orNull
              case clazz: ScTemplateDefinition => clazz.supers.headOption.map(_.getDocComment).orNull
              case _                           => null
            }

            if (superComment != null) {
              // TODO: should we handle inherited sections?
              val (inheritedDoc, _, sections) = getWikiTextRepresentation(superComment, macroFinder)
              val inheritedDocInner = inheritedDoc.toString.stripPrefix("/**").stripSuffix("*/")
              result.append(inheritedDocInner)
            }
          case _ =>
            result.append(elementText)
        }
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN
        if element.getParent.getParent.getFirstChild.textMatches(MyScaladocParsing.TYPE_PARAM_TAG) =>
        result.append("<" + elementText + ">")
      case ScalaDocTokenType.DOC_INNER_CODE_TAG =>
        val text =
          if (processingCustomTag) "<pre><code>"
          else " <pre> {@code "
        result.append(text)
      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG =>
        val text =
          if (processingCustomTag) "</code></pre>"
          else " } </pre> "
        result.append(text)
      case ScalaDocTokenType.VALID_DOC_HEADER =>
        val headerSize = if (elementText.length() <= 6) elementText.length() else 6
        result.append("<h" + headerSize + ">")
      case ScalaDocTokenType.DOC_HEADER =>
        if (element.getParent.getFirstChild.getNode.getElementType == ScalaDocTokenType.VALID_DOC_HEADER) {
          val headerSize = if (elementText.length() <= 6) elementText.length() else 6
          result.append("</h" + headerSize + ">")
        } else {
          result.append(elementText)
        }
      case ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
        result.append("<a href=\"")
      case ScalaDocTokenType.DOC_LINK_TAG =>
        result.append("{@link ")
      case ScalaDocTokenType.DOC_LINK_CLOSE_TAG =>
        if (element.getParent.getNode.getFirstChildNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG) {
          val linkText = element.getPrevSibling.getText
          if (linkText.trim().contains(" ")) {
            val trimmedText = linkText.trim()
            val spaceIndex = trimmedText.indexOf(" ")
            result.append(trimmedText.substring(0, spaceIndex)).append("\">")
            result.append(trimmedText.substring(spaceIndex + 1)).append("</a>")
          } else {
            result.append("\">" + linkText + "</a>")
          }
        } else {
          result.append("}")
        }
      case ScalaDocTokenType.DOC_COMMENT_DATA if element.getParent.isInstanceOf[ScDocTag] &&
        element.getParent.asInstanceOf[ScDocTag].name == MyScaladocParsing.SEE_TAG =>
        result.append("<dd>").append(elementText.trim()).append("</dd>")
      case ScalaDocTokenType.DOC_COMMENT_DATA
        if element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
        if (!elementText.trim().contains(" ")) {
          result.append(elementText)
        }
      case _ if replaceWikiScheme.contains(elementText) &&
        (element.getParent.getFirstChild == element || element.getParent.getLastChild == element) =>
        val prefix = if (element.getParent.getFirstChild == element) "<" else "</"
        result.append(prefix + replaceWikiScheme(elementText))
      case _ if element.getParent.getLastChild == element && // do not swap this & last cases
        replaceWikiScheme.contains(element.getParent.getFirstChild.getText) =>
        result.append(elementText).append("</")
        result.append(replaceWikiScheme(element.getParent.getFirstChild.getText))
      case ScalaDocTokenType.DOC_MACROS =>
        try
          macroFinder.getMacroBody(elementText.stripPrefix("$")) match {
            case Some(body) => result.append(body)
            case None       => result.append(s"[Cannot find macro: $elementText]")
          }
        catch {
          case _: Exception =>
        }
      case ScalaDocTokenType.DOC_WHITESPACE if elementText.contains("\n") =>
        // if it's trailing new line, do not add leading whitespaces
        // (in case the comment is intended far to the right), just add simple indent
        val isSpaceBetweenDocLines = elementText.contains("\n")
        if (isSpaceBetweenDocLines)
          result.append("\n ")
      case ScalaDocTokenType.DOC_COMMENT_END => // will be explicitly added in the end of comment processing
      case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS if processingCustomTag => // skip
      case _ =>
        result.append(elementText)
    }
  }

  private val replaceWikiScheme = Map(
    "__" -> "u>",
    "'''" -> "b>",
    "''" -> "i>",
    "`" -> "tt>",
    ",," -> "sub>",
    "^" -> "sup>"
  )
}
