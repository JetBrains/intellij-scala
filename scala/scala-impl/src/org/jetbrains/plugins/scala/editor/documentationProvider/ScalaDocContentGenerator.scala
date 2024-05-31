package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiElement, PsiErrorElement}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._
import org.jetbrains.plugins.scala.util.IndentUtil

import scala.collection.{Map, mutable}
import scala.util.{Failure, Success, Try}

/**
 * The class contains rendering logic of scala doc comment<br>
 * (links, lists, italic/bold, code blocks, and other "wiki" syntax)<br>
 *
 * It tries to be as close to the ScalaDoc tool logic as possible.
 *
 * @see [[https://docs.scala-lang.org/style/scaladoc.html]]
 * @see [[scala.tools.nsc.doc.base.CommentFactoryBase.WikiParser]]
 * @see [[scala.tools.nsc.doc.html.HtmlPage]]
 * @see java plugin analog [[com.intellij.codeInsight.javadoc.JavaDocInfoGenerator]]
 */
private class ScalaDocContentGenerator(
  originalComment: ScDocComment,
  macroFinder: MacroFinder,
) {

  import ApplicationManager.{getApplication => application}

  private val resolveContext: PsiElement = originalComment
  private var wikiSyntaxNestingLevel = 0
  private def isInWikiSyntaxElement = wikiSyntaxNestingLevel > 0

  def appendTagDescriptionText(
    buffer: StringBuilder,
    tag: ScDocTag
  ): Unit = {
    val descriptionParts = tagDescriptionParts(tag)
    appendDescriptionParts(buffer, descriptionParts)
  }

  @Nls
  def tagDescriptionText(
    tag: ScDocTag
  ): String = {
    val buffer = new StringBuilder
    appendTagDescriptionText(buffer, tag)
    buffer.result()
  }

  private def tagDescriptionParts(tag: ScDocTag): Iterable[ScDocDescriptionPart] = {
    val descriptionElements = Option(tag.getValueElement)
      .orElse(tag.children.findByType[ScStableCodeReference]) // for @throws tag
      .getOrElse(tag.getNameElement).nextSiblings
    descriptionElements.iterator.to(Iterable).filterByType[ScDocDescriptionPart]
  }

  private def nodesText(elements: Iterable[PsiElement]): String = {
    val buffer = new StringBuilder
    elements.foreach(visitNode(buffer, _))
    buffer.result()
  }

  def appendDescriptionParts(
    buffer: StringBuilder,
    parts: IterableOnce[ScDocDescriptionPart]
  ): Unit = {
    var isFirst = true
    parts.iterator.foreach { part =>
      visitDescriptionPartNode(buffer, part, isFirst)
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
        unresolvedReference(nodesText(element.children.filter(isContentChild).to(Iterable)))
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
      .map { (res: PsiElementResolveResult) =>
        val label = labelFromSiblings(ref, isContentChild).getOrElse(escapeHtml4(res.label))
        hyperLinkToPsi(res.refText, label, plainLink)
      }
      .getOrElse {
        unresolvedReference(labelFromSiblings(ref, isContentChild).getOrElse(escapeHtml4(ref.getText)))
      }
  }

  private def labelFromSiblings(element: PsiElement, siblingFilter: PsiElement => Boolean): Option[String] = {
    val labelElements = element.nextSiblings.dropLeadingDocWhitespaces.filter(siblingFilter)
    if (labelElements.nonEmpty) Some(nodesText(labelElements.to(Iterable))) else None
  }

  private def visitNodes(buffer: StringBuilder, elements: IterableOnce[PsiElement]): Unit =
    elements.iterator.foreach(visitNode(buffer, _))

  private def isLeaf(element: PsiElement): Boolean = element.getFirstChild == null

  private def visitNode(buffer: StringBuilder, element: PsiElement): Unit =
    if (isLeaf(element))
      visitLeafNode(buffer, element)
    else element match {
      case syntax: ScDocSyntaxElement  => visitSyntaxNode(buffer, syntax)
      case inlinedTag: ScDocInlinedTag => visitInlinedTag(buffer, inlinedTag)
      case list: ScDocList             => visitDocList(buffer, list)
      case code: ScDocInnerCodeElement => visitDocCode(buffer, code)
      case _                           => visitNodes(buffer, element.children)
    }

  private def calcMinIndent(children: Iterator[PsiElement], tabSize: Int): Option[Int] =
    children.collect {
      case el if el.getNode.getElementType == ScalaDocTokenType.DOC_INNER_CODE && el.getText.exists(!_.isWhitespace) =>
        IndentUtil.calcIndent(el.getText, tabSize)
    }.minOption

  private def visitDocCode(buffer: StringBuilder, code: ScDocInnerCodeElement): Unit = {
    val tabSize = CodeStyle.getIndentOptions(code.getContainingFile).TAB_SIZE
    val minIndent = calcMinIndent(code.children, tabSize)

    val codeBuilder = new StringBuilder()
    code.children.foreach { element =>
      element.getNode.getElementType match {
        case ScalaDocTokenType.DOC_INNER_CODE_TAG =>
        case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG =>
        case ScalaDocTokenType.DOC_INNER_CODE =>
          val text = minIndent.fold(element.getText)(element.getText.drop(_))
          codeBuilder.append(text)
        case ScalaDocTokenType.DOC_WHITESPACE if element.textContains('\n') =>
          codeBuilder.append("\n") // ignore other spaces except line break
        case _ => //just in case
      }
    }

    QuickDocHighlightingHelper.appendStyledCodeBlock(
      buffer.underlying,
      code.getProject,
      ScalaLanguage.INSTANCE, //NOTE: ScalaDoc tool always uses Scala language syntax highlighting (a least in Scala 2)
      codeBuilder.toString
    )
  }

  private def visitParagraph(buffer: StringBuilder, paragraph: ScDocParagraph, skipParagraphElement: Boolean): Unit = {
    def isEmpty(element: PsiElement): Boolean =
      element.elementType == ScalaDocTokenType.DOC_WHITESPACE ||
        element.elementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS

    val nodesTrimmed =
      paragraph
        .children
        .toList
        .dropWhile(isEmpty)
        .reverse
        .dropWhile(isEmpty)
        .reverse

    if (nodesTrimmed.nonEmpty) {
      val paragraphHasOnlyCodeSnippet = nodesTrimmed match {
        case List(_: ScDocInnerCodeElement) => true
        case _ => false
      }
      if (!paragraphHasOnlyCodeSnippet && !skipParagraphElement && !nodesTrimmed.head.getText.contains(HtmlStartParagraph))
        buffer.append(HtmlStartParagraph)

      nodesTrimmed.foreach(visitNode(buffer, _))

      if (!paragraphHasOnlyCodeSnippet && !skipParagraphElement && !nodesTrimmed.last.getText.contains(HtmlEndParagraph))
        buffer.append(HtmlEndParagraph)

      buffer.append('\n')
    }
  }

  private def visitDocList(buffer: StringBuilder, list: ScDocList): Unit = {
    val listItems = list.items
    val firstItem = listItems.head.headToken

    import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator.DocListType._

    val listType = listStyles.getOrElse(firstItem.getText, UnorderedList)

    val (htmlOpen, htmlClose) = listType match {
      case OrderedList(cssClass) => (s"""<ol class="$cssClass">""", """</ol>""")
      case UnorderedList         => (s"""<ul>""", """</ul>""")
    }

    buffer.append(htmlOpen)

    listItems.foreach { item =>
      buffer.append("<li>")
      val itemContentElements = item.headToken.nextSiblings.dropLeadingDocWhitespaces
      itemContentElements.foreach(visitNode(buffer, _))
      buffer.append("</li>")
    }

    buffer.append(htmlClose)
  }


  /**
   * JavaDoc-style inline tags, examples: {{{
   * {@code 2 + 2 == 42}
   * {@link scala.Exception}
   * }}}
   *
   * @see [[scala.tools.nsc.doc.base.CommentFactoryBase#javadocReplacement]]
   */
  //noinspection ScalaDocInlinedTag,ScalaDocParserErrorInspection
  private def visitInlinedTag(buffer: StringBuilder, inlinedTag: ScDocInlinedTag): Unit = {
    def appendHtmlTag(httpTag: String, inlinedTag: ScDocInlinedTag): Unit = {
      buffer.append(s"<$httpTag>")
      val innerChildren = inlinedTag.children.filter(isInlineInner)
      innerChildren.iterator.dropLeadingDocWhitespaces.foreach(visitLeafNode(buffer, _))
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
          wikiSyntaxNestingLevel += 1
          visitNodes(buffer, syntax.children.filter(isMarkupInner))
          wikiSyntaxNestingLevel -= 1
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
      elementType == ScalaDocTokenType.DOC_INLINE_TAG_END ||
      child.is[PsiErrorElement]
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
    val linkValue = linkElement.findFirstChildByType(ScalaDocTokenType.DOC_HTTP_LINK_VALUE).orNull
    if (linkValue == null) return None

    val href = linkValue.getText
    val labelNodes = linkValue.nextSiblings.dropLeadingDocWhitespaces.filter(isMarkupInner)
    val label = if (labelNodes.nonEmpty)
      nodesText(labelNodes.to(Iterable))
    else
      href
    Some(hyperLink(href, label))
  }

  private def visitLeafNode(result: StringBuilder, element: PsiElement): Unit =
    element.getNode.getElementType match {
      // leading '*' only can come from tags description, filtered for main content description
      case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS =>
      case ScalaDocTokenType.DOC_TAG_NAME                  =>
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN           =>
      case ScalaDocTokenType.DOC_MACROS                    => appendMacroValue(result, element)
      case _ if isDocLineBreak(element)                    => result.append("\n") // ignore other spaces except line break
      case _                                               =>
        val text1: String = unescape(element.getText)
        val text2: String = if (isInWikiSyntaxElement) escapeHtml4(text1) else text1
        result.append(text2)
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
    result.append(macroValue.subSequence(0, endIdx))
  }

  private def macroValueSafe(macroElement: PsiElement): String = {
    val macroKey = macroName(macroElement)
    val macroValue: Option[String] = Try(macroFinder.getMacroBody(macroKey)) match {
      case Success(value)     => value
      case Failure(_: ProcessCanceledException | _: IndexNotReadyException) =>
        None
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

  private val HtmlStartParagraph = "<p>"
  private val HtmlEndParagraph = "</p>"

  private case class PsiElementResolveResult(refText: String, label: String)

  def generatePsiElementLink(ref: ScStableCodeReference, context: PsiElement): String = {
    val resolved = resolvePsiElementLink(ref, context)
    resolved
      .map(res => hyperLinkToPsi(res.refText, escapeHtml4(res.label), plainLink = false))
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

  /**
   * TODO: unify with [[org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils.psiElementLink]]
   *  and [[org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils.psiElementLinkWithCodeTag]]
   */
  private def hyperLinkToPsi(refText: String, label: String, plainLink: Boolean): String = {
    val buffer = new java.lang.StringBuilder
    DocumentationManagerUtil.createHyperlink(buffer, refText, label, plainLink)
    buffer.toString
  }

  private def hyperLink(href: String, label: String): String =
    s"""<a href="${escapeHtml4(href)}">$label</a>""".stripMargin

  /** @note I considered using some reddish, wavy underline, but looks like Java Swing HTML/CSS renderer does not support
   *        text-decorator-style & text-decorator-color, see [[javax.swing.text.html.CSS]]. So for now we use just text.
   */
  private def unresolvedReference(text: String): String =
    if (StringUtils.isBlank(text)) ""
    else s"""<code>$text</code>"""

  private def markupTagToHtmlTag(markupTag: String): Option[String] = markupTag match {
    case "__"  => Some("u") // underline
    case "'''" => Some("b") // bold
    case "''"  => Some("i") // italic
    case "`"   => Some("tt") // monospace
    case ",,"  => Some("sub") // lower index
    case "^"   => Some("sup") // upper index
    case h if h.nonEmpty && h.forall(_ == '=') =>
      // NOTE: currently there is a bug in the IDEA Light Theme
      // in which html <h> tags are not rendered properly: IDEA-243159
      Some(s"h${h.length}")
    case _ => None
  }

  import DocListType._
  /** @see [[scala.tools.nsc.doc.base.CommentFactoryBase.WikiParser.listStyles]] */
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

  implicit class IteratorOps(private val elements: Iterator[PsiElement]) extends AnyVal {
    def dropLeadingDocWhitespaces: Iterator[PsiElement] =
      elements.dropWhile(_.elementType == ScalaDocTokenType.DOC_WHITESPACE)
  }
}

