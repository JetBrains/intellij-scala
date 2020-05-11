package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider.replaceWikiScheme
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.collection.mutable

private object ScaladocWikiProcessor {

  def replaceWikiWithTags(comment: ScDocComment): String = {
    val macroFinder = new MacroFinderImpl(comment, { element =>
      val a = getWikiTextRepresentation(new MacroFinderDummy)(element)
      a._1.result()
    })

    val (commentBody, tagsPart) = getWikiTextRepresentation(macroFinder)(comment)
    commentBody.append("<br/>\n").append(tagsPart).toString()
    commentBody.toString()
  }

  private def getWikiTextRepresentation(macroFinder: MacroFinder)(comment: PsiElement): (mutable.StringBuilder, mutable.StringBuilder) = {
    val commentBody = new StringBuilder("")
    val tagsPart = new StringBuilder("")
    var isFirst = true

    def visitTags(element: ScDocTag): Unit = {
      def visitChildren(output: mutable.StringBuilder): Unit =
        element.getNode.getChildren(null).map(_.getPsi).foreach(visitElementInner(_, output))

      element.name match {
        case MyScaladocParsing.TODO_TAG | MyScaladocParsing.NOTE_TAG |
             MyScaladocParsing.EXAMPLE_TAG | MyScaladocParsing.SEE_TAG =>
          if (isFirst)
            commentBody.append("<br/><br/>")
          isFirst = false
          visitChildren(commentBody)
          commentBody.append("<br/><br/>")
        case MyScaladocParsing.INHERITDOC_TAG =>
          visitChildren(commentBody)
        case _ =>
          visitChildren(tagsPart)
      }
    }

    def visitElementInner(element: PsiElement, result: StringBuilder): Unit =
      if (element.getFirstChild == null)
        visitElementInnerImpl(element, macroFinder, tagsPart, result)
      else
        for (child <- element.getNode.getChildren(null))
          child.getPsi match {
            case tag: ScDocTag => visitTags(tag)
            case _             => visitElementInner(child.getPsi, result)
          }

    visitElementInner(comment, commentBody)
    (commentBody, tagsPart)
  }

  private def visitElementInnerImpl(
    element: PsiElement,
    macroFinder: MacroFinder,
    tagsPart: mutable.StringBuilder,
    result: mutable.StringBuilder
  ): Unit =
    element.getNode.getElementType match {
      case ScalaDocTokenType.DOC_TAG_NAME =>
        element.getText match {
          case MyScaladocParsing.TYPE_PARAM_TAG =>
            result.append("@param ")
          case MyScaladocParsing.NOTE_TAG | MyScaladocParsing.TODO_TAG | MyScaladocParsing.EXAMPLE_TAG =>
            result.append("<b>").append(element.getText.substring(1).capitalize).append(":</b><br/>")
          case MyScaladocParsing.SEE_TAG =>
            result.append("<b>").append("See also").append(":</b><br/>")
          case MyScaladocParsing.INHERITDOC_TAG =>
            val inherited = element.getParent.getParent.getParent match {
              case fun: ScFunction             => (fun.superMethod map (_.getDocComment)).orNull
              case clazz: ScTemplateDefinition => (clazz.supers.headOption map (_.getDocComment)).orNull
              case _                           => null
            }

            if (inherited != null) {
              val (inheritedBody, _) = getWikiTextRepresentation(macroFinder)(inherited)
              result append inheritedBody.toString().stripPrefix("/**").stripSuffix("*/")
            }
          case _ =>
            result.append(element.getText)
        }
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN
        if element.getParent.getParent.getFirstChild.textMatches(MyScaladocParsing.TYPE_PARAM_TAG) =>
        result.append("<" + element.getText + ">")
      case ScalaDocTokenType.DOC_INNER_CODE_TAG =>
        result.append(" <pre> {@code ")
      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG =>
        result.append(" } </pre> ")
      case ScalaDocTokenType.VALID_DOC_HEADER =>
        val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
        result.append("<h" + headerSize + ">")
      case ScalaDocTokenType.DOC_HEADER =>
        if (element.getParent.getFirstChild.getNode.getElementType == ScalaDocTokenType.VALID_DOC_HEADER) {
          val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
          result.append("</h" + headerSize + ">")
        } else {
          result.append(element.getText)
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
            result.append(trimmedText.substring(0, spaceIndex)).append("\">").append(trimmedText.substring(spaceIndex + 1)).append("</a>")
          } else {
            result.append("\">" + linkText + "</a>")
          }
        } else {
          result.append("}")
        }
      case ScalaDocTokenType.DOC_COMMENT_DATA if element.getParent.isInstanceOf[ScDocTag] &&
        element.getParent.asInstanceOf[ScDocTag].name == MyScaladocParsing.SEE_TAG =>
        result.append("<dd>").append(element.getText.trim()).append("</dd>")
      case ScalaDocTokenType.DOC_COMMENT_DATA
        if element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
        if (!element.getText.trim().contains(" ")) {
          result.append(element.getText)
        }
      case _ if replaceWikiScheme.contains(element.getText) &&
        (element.getParent.getFirstChild == element || element.getParent.getLastChild == element) =>
        val prefix = if (element.getParent.getFirstChild == element) "<" else "</"
        result.append(prefix + replaceWikiScheme(element.getText))
      case _ if element.getParent.getLastChild == element && // do not swap this & last cases
        replaceWikiScheme.contains(element.getParent.getFirstChild.getText) =>
        result.append(element.getText).append("</")
        result.append(replaceWikiScheme(element.getParent.getFirstChild.getText))
      case ScalaDocTokenType.DOC_COMMENT_END =>
        tagsPart.append(element.getText)
      case ScalaDocTokenType.DOC_MACROS =>
        try
          macroFinder.getMacroBody(element.getText.stripPrefix("$")) match {
            case Some(body) => result.append(body)
            case None       => result.append(s"[Cannot find macro: ${element.getText}]")
          }
        catch {
          case _: Exception =>
        }
      case _ =>
        result.append(element.getText)
    }
}
