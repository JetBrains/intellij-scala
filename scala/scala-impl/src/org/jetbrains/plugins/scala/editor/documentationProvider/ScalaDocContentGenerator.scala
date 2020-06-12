package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.text.CharSequenceSubSequence
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator._
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.TraversableOnce
import scala.util.Try

// TODO: propper naming
private class ScalaDocContentGenerator(
  resolveContext: PsiElement,
  macroFinder: MacroFinder,
  rendered: Boolean // TODO: use
) {

  private var blankLinesCount = 0
  private var isLeadingSpaces = true

  /** counter used to prevent inserting paragraph break inside code examples or explicit "pre" html tags */
  private var preTagNesting = 0

  def appendCommentDescription(
    buffer: StringBuilder,
    comment: ScDocComment
  ): Unit =
    appendNodesText(buffer, comment.getDescriptionElements)

  def appendNodesText(
    buffer: StringBuilder,
    elements: Seq[PsiElement]
  ): Unit =
    elements.foreach(visitNode(buffer, _))

  def appendNodeText(
    buffer: StringBuilder,
    element: PsiElement
  ): Unit =
    visitNode(buffer, element)

  def nodesText(elements: TraversableOnce[PsiElement]): String = {
    val buffer = StringBuilder.newBuilder
    elements.foreach(visitNode(buffer, _))
    buffer.result
  }

  def nodeText(element: PsiElement): String = {
    val buffer = StringBuilder.newBuilder
    visitNode(buffer, element)
    buffer.result
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

  private def generatePsiElementLinkWithLabel(
    ref: ScStableCodeReference,
    plainLink: Boolean,
    isContentChild: PsiElement => Boolean
  ): String = {
    val resolved = resolvePsiElementLink(ref, resolveContext)
    resolved
      .map { res: PsiElementResolveResult =>
        val label = labelFromSiblings(ref, isContentChild).getOrElse(escapeHtml(res.shortestName))
        hyperLinkToPsi(res.qualifiedName, label, plainLink)
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
      case syntax: ScDocSyntaxElement =>
        visitSyntaxNode(buffer, syntax)
      case inlinedTag: ScDocInlinedTag =>
        visitInlinedTag(buffer,inlinedTag )
      case _ =>
        element.children.foreach(visitNode(buffer,_))
    }
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
    val markupTagElement = syntax.firstChild.filter(_.elementType.isInstanceOf[ScaladocSyntaxElementType])
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
      case _: LeafPsiElement => !child.elementType.isInstanceOf[ScaladocSyntaxElementType]
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
    val elementText = element.getText
    val elementType = element.getNode.getElementType

    // TODO: create a ticket for a background change
    //      case ScalaDocTokenType.DOC_INNER_CODE_TAG            => result.append("""<pre style="background: red"><code>""");
    //      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG      => result.append("""</code></pre>""");

    val resultLengthBefore = result.length

    val appendText: Option[String] = elementType match {
      // leading '*' only can come from tags description, filtered for main content description
      case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS => None
      case ScalaDocTokenType.DOC_TAG_NAME                  => None
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN           => None
      case ScalaDocTokenType.DOC_INNER_CODE_TAG            => Some("""<pre><code>""");
      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG      => Some("""</code></pre>""");
      case ScalaDocTokenType.DOC_COMMENT_DATA              => Some(elementText)
      case ScalaDocTokenType.DOC_MACROS                    => Some(macroValueSafe(macroFinder, elementText.stripPrefix("$")))
      case _ if isDocLineBreak(element)                    => handleLineBreak(element)
      case _                                               => Some(elementText)
    }

    appendText match {
      case Some(text) =>
        result.append(text)
        if (elementType != ScalaDocTokenType.DOC_WHITESPACE)
          isLeadingSpaces = false
      case None        =>
    }

    updatePreTagsNesting(result, resultLengthBefore)
  }

  private def handleLineBreak(lineBreakElement: PsiElement): Option[String] = {
    blankLinesCount += 1

    val firstLineElement = lineBreakElement.nextSibling
      .filter(_.elementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)
      .flatMap(_.nextSibling)

    firstLineElement match {
      case Some(firstElement) =>
        val lineContainsContent = !isDocLineBreak(firstElement)
        if (lineContainsContent) {
          val startNewParagraph = blankLinesCount > 1 &&
            !isLeadingSpaces &&
            preTagNesting == 0 &&
            !firstElement.isInstanceOf[ScDocTag] &&
            !firstElement.getNextSibling.isInstanceOf[ScDocTag]

          blankLinesCount = 0
          if (startNewParagraph) Some("\n <p>") else Some("\n ")
        } else Some("\n ")
      case None => None // can be empty for the last tag child element (it's dangling new line DOC_WHITESPACE_
    }
  }

  private def updatePreTagsNesting(builder: StringBuilder, fromIndex: Int): Unit = {
    // char sequence view that doesn't do any copies
    val addedContent = new CharSequenceSubSequence(builder, fromIndex, builder.length)
    PreTagRegex.findAllMatchIn(addedContent).foreach { found =>
      val opening = addedContent.charAt(found.start + 1) != '/'
      preTagNesting += (if (opening) 1 else -1)
      preTagNesting = preTagNesting.max(0)
    }
  }

  private def isDocLineBreak(element: PsiElement): Boolean =
    element.elementType == ScalaDocTokenType.DOC_WHITESPACE && element.textContains('\n')
}

object ScalaDocContentGenerator {

  private val Log = Logger.getInstance(classOf[ScalaDocContentWithSectionsGenerator])

  private case class PsiElementResolveResult(qualifiedName: String, shortestName: String)

  def generatePsiElementLink(ref: ScStableCodeReference, context: PsiElement): String = {
    val resolved = resolvePsiElementLink(ref, context)
    resolved
      .map(res => hyperLinkToPsi(res.qualifiedName, escapeHtml(res.shortestName), plainLink = false))
      .getOrElse(unresolvedReference(ref.getText))
  }

  private def resolvePsiElementLink(ref: ScStableCodeReference, context: PsiElement): Option[PsiElementResolveResult] = {
    lazy val refText = ref.getText.trim
    val resolveResults = ref.multiResolveScala(false)
    for {
      resolveResult <- resolveResults match {
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
      resolved = resolveResult.element
      qualifiedName <- qualifiedNameForElement(resolved)
    } yield {
      val shortestName = resolved match {
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

  private def macroValueSafe(macroFinder: MacroFinder, macroKey: String): String = {
    val macroValue: Option[String] = Try(macroFinder.getMacroBody(macroKey))
      .recover { case exception =>
        Log.debug(s"Error occurred during macro resolving: $macroKey", exception)
        None
      }
      .get
    macroValue.getOrElse(s"[Cannot find macro: $$$macroKey]")
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

  private val PreTagRegex = """</?pre\s*>""".r // TODO: extract
}

