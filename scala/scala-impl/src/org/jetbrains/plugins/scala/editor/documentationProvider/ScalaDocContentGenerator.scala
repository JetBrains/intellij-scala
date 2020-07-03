package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiElement}
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator._
import org.jetbrains.plugins.scala.extensions.{&&, ElementType, IteratorExt, PrevLeaf, PsiClassExt, PsiElementExt, PsiMemberExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.{Map, TraversableOnce, mutable}
import scala.util.{Failure, Success, Try}

/**
 * @see [[scala.tools.nsc.doc.base.CommentFactoryBase.WikiParser]]
 * @see [[scala.tools.nsc.doc.html.HtmlPage]]
 */
private class ScalaDocContentGenerator(
  originalComment: ScDocComment,
  macroFinder: MacroFinder,
  rendered: Boolean // TODO: use
) {

  import ApplicationManager.{getApplication => application}

  private val resolveContext: PsiElement = originalComment

  import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator.DocListType._

  def appendCommentDescription(
    buffer: StringBuilder,
    comment: ScDocComment // can be comment of a super method
  ): Unit = {
    val parts = comment.getDescriptionElements.toTraversable.filterBy[ScDocDescriptionPart]
    appendDescriptionParts(buffer, parts)
  }

  def appendTagDescriptionText(
    buffer: StringBuilder,
    tag: ScDocTag
  ): Unit = {
    val descriptionParts = tagDescriptionParts(tag)
    appendDescriptionParts(buffer, descriptionParts, skipFirstParagraph = true)
  }

  def tagDescriptionText(
    tag: ScDocTag
  ): String = {
    val buffer = newStringBuilder
    appendTagDescriptionText(buffer, tag)
    buffer.result
  }

  private def tagDescriptionParts(tag: ScDocTag): Traversable[ScDocDescriptionPart] = {
    val descriptionElements = Option(tag.getValueElement)
      .orElse(tag.children.findByType[ScStableCodeReference]) // for @throws tag
      .getOrElse(tag.getNameElement).nextSiblings
    descriptionElements.toTraversable.filterBy[ScDocDescriptionPart]
  }

  private def nodesText(elements: TraversableOnce[PsiElement]): String = {
    val buffer = newStringBuilder
    elements.foreach(visitNode(buffer, _))
    buffer.result
  }

  private def appendDescriptionParts(
    buffer: StringBuilder,
    parts: TraversableOnce[ScDocDescriptionPart],
    skipFirstParagraph: Boolean = false
  ): Unit = {
    var isFirst = true
    parts.foreach { part =>
      visitDescriptionPartNode(buffer, part, isFirst && skipFirstParagraph)
      isFirst = false
    }
  }

  private def generatePsiElementLinkWithLabelForChildren(
    element: PsiElement,
    plainLink: Boolean,
    isContentChild: PsiElement => Boolean
  ): String = {
    val result = for {
      ref <- element.children.findByType[ScStableCodeReference].toRight {
        unresolvedReference(nodesText(element.children.filter(isContentChild)))
      }
    } yield generatePsiElementLinkWithLabel(ref, plainLink, isContentChild)
    result.merge
  }

  // NOTE: assuming nested ScDocDescriptionPart is not the case
  private def visitDescriptionPartNode(buffer: StringBuilder, part: ScDocDescriptionPart, skipParagraphElement: Boolean): Unit =
    part match {
      case paragraph: ScDocParagraph   => visitParagraph(buffer, paragraph, skipParagraphElement)
      case list: ScDocList             => visitDocList(buffer, list)
      case _                           => // impossible case
    }

  private def generatePsiElementLinkWithLabel(
    ref: ScStableCodeReference,
    plainLink: Boolean,
    isContentChild: PsiElement => Boolean
  ): String = {
    val resolved = resolvePsiElementLink(ref, resolveContext)
    resolved
      .map { res: PsiElementResolveResult =>
        val label = labelFromSiblings(ref, isContentChild).getOrElse(escapeHtml(res.label))
        hyperLinkToPsi(res.refText, label, plainLink)
      }
      .getOrElse {
        unresolvedReference(labelFromSiblings(ref, isContentChild).getOrElse(escapeHtml(ref.getText)))
      }
  }

  private def labelFromSiblings(element: PsiElement, siblingFilter: PsiElement => Boolean): Option[String] = {
    val labelElements = element.nextSiblings.dropWhile(_.elementType == ScalaDocTokenType.DOC_WHITESPACE).filter(siblingFilter)
    if (labelElements.nonEmpty) Some(nodesText(labelElements)) else None
  }

  private def visitNodes(buffer: StringBuilder, elements: TraversableOnce[PsiElement]): Unit =
    elements.foreach(visitNode(buffer, _))

  private def visitNode(buffer: StringBuilder, element: PsiElement): Unit = {
    val isLeafNode = element.getFirstChild == null
    if (isLeafNode)
      visitLeafNode(buffer, element)
    else element match {
      case syntax: ScDocSyntaxElement  => visitSyntaxNode(buffer, syntax)
      case inlinedTag: ScDocInlinedTag => visitInlinedTag(buffer, inlinedTag)
      case list: ScDocList             => visitDocList(buffer, list)
      case _                           => element.children.foreach(visitNode(buffer, _))
    }
  }

  private def visitParagraph(buffer: StringBuilder, paragraph: ScDocParagraph, skipParagraphElement: Boolean): Unit = {
    if (!skipParagraphElement)
      buffer.append(HtmlParagraph)
    paragraph.children
      .dropWhile(_.elementType == ScalaDocTokenType.DOC_WHITESPACE)
      .foreach(visitNode(buffer, _))
  }

  private def visitDocList(buffer: StringBuilder, list: ScDocList): Unit = {
    val listItems = list.items
    val firstItem = listItems.head.headToken

    val listType = listStyles.getOrElse(firstItem.getText, UnorderedList)

    val (htmlOpen, htmlClose) = listType match {
      case OrderedList(cssClass) => (s"""<ol class="$cssClass">""", """</ol>""")
      case UnorderedList         => (s"""<ul>""", """</ul>""")
    }

    buffer.append(htmlOpen)

    listItems.foreach { item =>
      buffer.append("<li>")
      val itemContentElements = item.headToken.nextSiblings
      itemContentElements.foreach(visitNode(buffer, _))
      buffer.append("</li>")
    }

    buffer.append(htmlClose)
  }


  /**
   * JavaDoc-style inline tags e.g. {@code 2 + 2 == 42} or {@link scala.Exception}
   *
   * @see [[scala.tools.nsc.doc.base.CommentFactoryBase#javadocReplacement]]
   */
  //noinspection ScalaDocInlinedTag,ScalaDocParserErrorInspection
  private def visitInlinedTag(buffer: StringBuilder, inlinedTag: ScDocInlinedTag): Unit = {
    def appendHtmlTag(httpTag: String, inlinedTag: ScDocInlinedTag): Unit = {
      buffer.append(s"<$httpTag>")
      inlinedTag.children.filter(isInlineInner).foreach(visitLeafNode(buffer, _))
      buffer.append(s"</$httpTag>")
    }

    val tagName = inlinedTag.name
    tagName match {
      case "docRoot"           => // ignore
      case "code"              => appendHtmlTag("code", inlinedTag)
      case "literal" | "value" => appendHtmlTag("tt", inlinedTag)
      case "link"              => buffer.append(generatePsiElementLinkWithLabelForChildren(inlinedTag, plainLink = false, isInlineInner))
      case "linkplain"         => buffer.append(generatePsiElementLinkWithLabelForChildren(inlinedTag, plainLink = true, isInlineInner))
      case _                   => buffer.append(inlinedTag.getText)
    }
  }

  private def visitSyntaxNode(buffer: StringBuilder, syntax: ScDocSyntaxElement): Unit = {
    val markupTagElement = syntax.firstChild.filter(_.elementType.isInstanceOf[ScalaDocSyntaxElementType])
    val markupTag = markupTagElement.map(_.getText)
    if (markupTag.contains("[["))
      buffer.append(generateLink(syntax))
    else
      markupTag.flatMap(markupTagToHtmlTag) match {
        case Some(htmlTag) =>
          buffer.append(s"<$htmlTag>")
          visitNodes(buffer, syntax.children.filter(isMarkupInner))
          buffer.append(s"</$htmlTag>")
        case None          =>
          visitNodes(buffer, syntax.children)
      }
  }

  // wrong markup can contain no closing tag e.g. `__text` (it's wrong but we handle anyway and show inspection)
  private def isMarkupInner(child: PsiElement): Boolean =
    child match {
      case _: LeafPsiElement => !child.elementType.isInstanceOf[ScalaDocSyntaxElementType]
      case _                 => true
    }

  private def isInlineInner(child: PsiElement): Boolean = {
    val elementType = child.elementType
    val skip = elementType == ScalaDocTokenType.DOC_TAG_NAME ||
      elementType == ScalaDocTokenType.DOC_INLINE_TAG_START ||
      elementType == ScalaDocTokenType.DOC_INLINE_TAG_END
    !skip
  }

  private def generateLink(linkElement: ScDocSyntaxElement): String = {
    val firstChild = linkElement.getFirstChild

    val isHttpLink = firstChild.elementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG
    if (isHttpLink)
      generateHttpLink(linkElement).getOrElse(linkElement.getText)
    else
      generatePsiElementLinkWithLabelForChildren(linkElement, plainLink = false, isMarkupInner)
  }

  private def generateHttpLink(linkElement: ScDocSyntaxElement): Option[String] = {
    val linkValue = linkElement.findFirstChildByType(ScalaDocTokenType.DOC_HTTP_LINK_VALUE)
    if (linkValue == null) return None

    val href = linkValue.getText
    val labelNodes = linkValue.nextSiblings.dropWhile(_.elementType == ScalaDocTokenType.DOC_WHITESPACE).filter(isMarkupInner)
    val label = if (labelNodes.nonEmpty)
      nodesText(labelNodes)
    else
      href
    Some(hyperLink(href, label))
  }

  private def visitLeafNode(
    result: StringBuilder,
    element: PsiElement
  ): Unit = {
    val elementType = element.getNode.getElementType
    elementType match {
      // leading '*' only can come from tags description, filtered for main content description
      case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS => None
      case ScalaDocTokenType.DOC_TAG_NAME                  => None
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN           => None
      case ScalaDocTokenType.DOC_INNER_CODE_TAG            => result.append("""<pre><code>""")
      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG      => result.append("""</code></pre>""")
      case ScalaDocTokenType.DOC_MACROS                    => appendMacroValue(result, element)
      case _ if isDocLineBreak(element)                    => result.append("\n") // ignore other spaces except line break
      case _                                               => result.append(unescape(element.getText))
    }
  }

  private def unescape(text: String): String = {
    val escapedDollar = "\\$"
    if (text.contains(escapedDollar))
      text.replace(escapedDollar, "$")
    else text
  }

  private def appendMacroValue(result: StringBuilder, macroElement: PsiElement): Unit = {
    val macroValue = macroValueSafe(macroElement)
    val endIdx = macroValue.lastIndexWhere(!_.isWhitespace) + 1
    result.append(macroValue, 0, endIdx)
  }

  private def macroValueSafe(macroElement: PsiElement): String = {
    val macroKey = macroName(macroElement)
    val macroValue: Option[String] = Try(macroFinder.getMacroBody(macroKey)) match {
      case Success(value)     => value
      case Failure(exception) =>
        val message = s"Error occurred during macro resolving: $macroKey"
        if (application.isInternal || application.isUnitTestMode)
          Log.error(message, exception)
        else
          Log.debug(message, exception)
        None
    }

    if (macroValue.isEmpty && application.isUnitTestMode) {
      val commentOwner = originalComment.getOwner
      val info = UnresolvedMacroInfo(commentOwner.getContainingFile.getVirtualFile, commentOwner.getName, macroKey)
      unresolvedMacro.append(info)
    }

    macroValue.getOrElse(macroElement.getText)
  }

  private def macroName(macroElement: PsiElement): String =
    macroElement.getText.stripPrefix("$").stripPrefix("{").stripSuffix("}")

  private def isDocLineBreak(element: PsiElement): Boolean =
    element.elementType == ScalaDocTokenType.DOC_WHITESPACE && element.textContains('\n')
}

object ScalaDocContentGenerator {

  private val Log = Logger.getInstance(classOf[ScalaDocContentGenerator])

  private val HtmlParagraph = "<p>"

  private case class PsiElementResolveResult(refText: String, label: String)

  def generatePsiElementLink(ref: ScStableCodeReference, context: PsiElement): String = {
    val resolved = resolvePsiElementLink(ref, context)
    resolved
      .map(res => hyperLinkToPsi(res.refText, escapeHtml(res.label), plainLink = false))
      .getOrElse(unresolvedReference(ref.getText))
  }

  private def resolvePsiElementLink(ref: ScStableCodeReference, context: PsiElement): Option[PsiElementResolveResult] = {
    lazy val refText = ref.getText.trim
    val resolveResults = ref.multiResolveScala(false)
    val singleResolveResult = resolveResults match {
      case Array(head) => Some(head)
      case companions if companions.length == 2 =>
        // TODO: this actually can be triggered for non companions but e.g. for
        //  type :: = String
        //  val :: = 42
        val selectCompanion = refText.endsWith("$")
        val result = if (selectCompanion)
          companions.find(_.element.isInstanceOf[ScObject])
        else
          companions.find(!_.element.isInstanceOf[ScObject])
        result.orElse(companions.find(_.element.isInstanceOf[ScTypeAlias]))
      case _ => None
    }

    val resolvedElement = singleResolveResult.map(_.element)
    resolvedElement match {
      case Some(function: ScFunction) =>
        val clazz: PsiClass = function.containingClass
        if (clazz!= null) {
          val fqn = clazz.qualifiedName
          if (fqn != null) {
            val result = Some(PsiElementResolveResult(s"${clazz.qualifiedName}#${function.name}", ref.getText))
            return result
          }
        }
      case _                          =>
    }

    for {
      element       <- resolvedElement
      qualifiedName <- qualifiedNameForElement(element)
    } yield {
      val shortestName = element match {
        case clazz: PsiClass        => ScalaDocUtil.shortestClassName(clazz, context)
        case typeAlias: ScTypeAlias => ScalaDocUtil.shortestClassName(typeAlias, context)
        case _                      => refText
      }
      PsiElementResolveResult(qualifiedName, shortestName)
    }
  }

  private def qualifiedNameForElement(element: PsiElement): Option[String] =
    element match {
      case clazz: PsiClass    => Option(clazz.qualifiedName)
      case alias: ScTypeAlias => alias.qualifiedNameOpt
      case _                  => None
    }

  private def hyperLinkToPsi(refText: String, label: String, plainLink: Boolean): String = {
    val buffer = new java.lang.StringBuilder
    DocumentationManagerUtil.createHyperlink(buffer, refText, label, plainLink)
    buffer.toString
  }

  private def hyperLink(href: String, label: String): String =
    s"""<a href="${escapeHtml(href)}">$label</a>""".stripMargin

  /**
   * TODO: do not make it so annoying red
   *  especially in in-editor mode (rendered)
   *  consider some underline?
   */
  private def unresolvedReference(text: String): String = s"<font color=red>$text</font>"

  private def isLeadingDocLineElement(element: PsiElement): Boolean = {
    import ScalaDocTokenType._
    element match {
      case PrevLeaf(ElementType(DOC_WHITESPACE) && PrevLeaf(ElementType(DOC_COMMENT_LEADING_ASTERISKS))) |
           PrevLeaf(ElementType(DOC_COMMENT_LEADING_ASTERISKS)) => true
      case _ => false
    }
  }

  private def isFirstParagraphChild(element: PsiElement): Boolean =
    element.getParent match {
      case p: ScDocParagraph =>
        p.getFirstChild match {
          case null                                             => false
          case ws@ElementType(ScalaDocTokenType.DOC_WHITESPACE) => ws.getNextSibling == element
          case `element`                                        => true
        }
      case _ => false
    }

  private def markupTagToHtmlTag(markupTag: String): Option[String] = markupTag match {
    case "__"  => Some("u")
    case "'''" => Some("b")
    case "''"  => Some("i")
    case "`"   => Some("tt")
    case ",,"  => Some("sub")
    case "^"   => Some("sup")
    case h if h.nonEmpty && h.forall(_ == '=') =>
      // NOTE: currently there is a bug in the IDEA Light Theme
      // in which html <h> tags are not rendered properly: IDEA-243159
      Some(s"h${h.length}")
    case _ => None
  }

  import DocListType._
  /** @see [[scala.tools.nsc.doc.base.CommentFactoryBase.WikiParser#listStyles]] */
  private val listStyles: Map[String, DocListType] = Map(
    "-"  -> UnorderedList,
    "1." -> OrderedList("decimal"),
    "I." -> OrderedList("upperRoman"),
    "i." -> OrderedList("lowerRoman"),
    "A." -> OrderedList("upperAlpha"),
    "a." -> OrderedList("lowerAlpha")
  )

  private sealed trait DocListType
  private object DocListType {
    final case class OrderedList(cssClass: String) extends DocListType
    final object UnorderedList extends DocListType
  }

  @TestOnly
  case class UnresolvedMacroInfo(file: VirtualFile, commentOwnerName: String, macroKey: String)
  @TestOnly
  lazy val unresolvedMacro: mutable.Buffer[UnresolvedMacroInfo] =
    mutable.ArrayBuffer.empty[UnresolvedMacroInfo]
}

