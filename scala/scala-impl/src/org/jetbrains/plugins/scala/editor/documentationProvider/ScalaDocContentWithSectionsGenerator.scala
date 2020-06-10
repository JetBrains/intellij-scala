package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement}
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentWithSectionsGenerator._
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.TraversableOnce
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * @note for the current moment (8 June 2020) there is no any Scala Doc specification.<br>
 *       From the gitter channel [[https://gitter.im/scala/contributors?at=5eda0c937da67d06faf2e43e]]:<br>
 *       "I'm pretty sure that no such (spec) thing exists if you want to try and extract one yourself,
 *       the main place to look would be [[scala.tools.nsc.doc.base.CommentFactoryBase]]"
 */
//noinspection ScalaDocInlinedTag,ScalaDocParserErrorInspection
// TODO: remove maximum common indentation from code examples {{{ }}} not to shift it far to the right
// TODO: from https://docs.scala-lang.org/overviews/scaladoc/for-library-authors.html#markup
//  Comment Inheritance - Implicit
//  If a comment is not provided for an entity at the current inheritance level, but is supplied for the overridden entity at a higher level
//  in the inheritance hierarchy, the comment from the super-class will be used.
//  Likewise if @param, @tparam, @return and other entity tags are omitted but available from a superclass, those comments will be used.
private class ScalaDocContentWithSectionsGenerator private(
  comment: ScDocComment,
  macroFinder: MacroFinder
) {

  private val resolveContext: PsiElement = comment

  private def this(
    commentOwner: ScDocCommentOwner,
    comment: ScDocComment,
  ) = this(
    comment,
    new MacroFinderImpl(commentOwner, renderDefineTagChildElement)
  )

  def generate(
    buffer: StringBuilder,
    rendered: Boolean // TODO: use
  ): Unit = {

    val tags: Seq[ScDocTag] = comment.tags

    buffer.append(DocumentationMarkup.CONTENT_START)
    generateContentWithInherited(buffer, tags.find(_.name == MyScaladocParsing.INHERITDOC_TAG))
    buffer.append("<p>")
    buffer.append(DocumentationMarkup.CONTENT_END)

    val sections = buildSections(tags)
    if (sections.nonEmpty) {
      buffer.append(DocumentationMarkup.SECTIONS_START)
      appendSections(sections, buffer)
      buffer.append(DocumentationMarkup.SECTIONS_END)
    }
  }

  private def appendSections(sections: Seq[Section], result: StringBuilder): Unit =
    sections.foreach { section =>
      import DocumentationMarkup._
      result
        .append(SECTION_HEADER_START)
        .append(section.title)
        .append(SECTION_SEPARATOR)
        .append(section.content)
        .append(SECTION_END)
    }


  private def buildSections(tags: Seq[ScDocTag]): Seq[Section] = {
    val sections = ArrayBuffer.empty[Section]

    sections ++= prepareSimpleSections(tags, MyScaladocParsing.DEPRECATED_TAG, ScalaEditorBundle.message("scaladoc.section.deprecated"))

    val paramsSection     = prepareParamsSection(tags)
    val typeParamsSection = prepareTypeParamsSection(tags)
    val returnsSection    = prepareReturnsSection(tags)
    val throwsSection     = prepareThrowsSection(tags)

    sections ++=
      paramsSection ++=
      typeParamsSection ++=
      returnsSection ++=
      throwsSection

    sections ++=
      prepareSimpleSections(tags, MyScaladocParsing.NOTE_TAG, ScalaEditorBundle.message("scaladoc.section.note")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.EXAMPLE_TAG, ScalaEditorBundle.message("scaladoc.section.example")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.SEE_TAG, ScalaEditorBundle.message("scaladoc.section.see.also")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.SINCE_TAG, ScalaEditorBundle.message("scaladoc.section.since")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.TODO_TAG, ScalaEditorBundle.message("scaladoc.section.todo"))

    sections
  }

  private def prepareSimpleSections(tags: Seq[ScDocTag], tagName: String, sectionTitle: String): Seq[Section] = {
    val matchingTags = tags.filter(_.name == tagName)
    matchingTags.map { tag =>
      val sectionContent = nodesText(tag.children)
      Section(sectionTitle, sectionContent.trim)
    }
  }

  private def prepareParamsSection(tags: Seq[ScDocTag]) = {
    val paramTags = tags.filter(_.name == MyScaladocParsing.PARAM_TAG)
    val paramTagsInfo = paramTags.flatMap(parameterInfo)
    if (paramTagsInfo.nonEmpty) {
      val content = parameterInfosText(paramTagsInfo)
      Some(Section("Params:", content))
    } else None
  }

  private def prepareTypeParamsSection(tags: Seq[ScDocTag]) = {
    val typeParamTags = tags.filter(_.name == MyScaladocParsing.TYPE_PARAM_TAG)
    val typeParamTagsInfo = typeParamTags.flatMap(parameterInfo)
    if (typeParamTagsInfo.nonEmpty) {
      val content = parameterInfosText(typeParamTagsInfo)
      Some(Section("Type parameters:", content))
    } else None
  }

  private def prepareReturnsSection(tags: Seq[ScDocTag]) = {
    // TODO: if there is inherited doc, get return description from there
    val returnTag = tags.find(_.name == MyScaladocParsing.RETURN_TAG)
    returnTag.map(innerContentText(_)).map(Section("Returns:", _))
  }

  private def prepareThrowsSection(tags: Seq[ScDocTag]) = {
    val throwTags      = tags.filter(_.name == MyScaladocParsing.THROWS_TAG)
    val throwTagsInfos = throwTags.flatMap(throwsInfo)
    if (throwTagsInfos.nonEmpty) {
      val content = parameterInfosText(throwTagsInfos)
      Some(Section("Throws:", content))
    } else None
  }

  private def parameterInfo(tag: ScDocTag): Option[ParamInfo] =
    tag.children.findByType[ScDocTagValue].map { tagValue =>
      val tagDescription = StringBuilder.newBuilder
      tagValue.nextSiblings.foreach(visitNode(tagDescription, _))
      ParamInfo(tagValue.getText, tagDescription.result())
    }

  private def throwsInfo(tag: ScDocTag): Option[ParamInfo] = {
    val exceptionRef = tag.children.findByType[ScStableCodeReference]
    exceptionRef.map { ref =>
      val value = generatePsiElementLink(ref, resolveContext)
      val description = nodesText(ref.nextSiblings)
      ParamInfo(value, description)
    }
  }

  private def parameterInfosText(infos: Seq[ParamInfo]): String =
    infos.map(p => s"${p.value} &ndash; ${p.description.trim}").mkString("<p>")

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

  private def generateContent(
    buffer: StringBuilder
  ): Unit = {
    val contentElements = comment.getDescriptionElements
    contentElements.foreach(visitNode(buffer, _))
  }

  private def generateContentWithInherited(
    buffer: StringBuilder,
    inheritDocTagOpt: Option[ScDocTag]
  ): Unit = {
    generateContent(buffer)

    inheritDocTagOpt.foreach { inheritDocTag =>
      addInheritedDocText(buffer)
      visitNode(buffer, inheritDocTag)
    }
  }

  private def addInheritedDocText(buffer: StringBuilder): Unit = {
    val superCommentOwner: Option[PsiDocCommentOwner] = comment.getOwner match {
      case fun: ScFunction             => fun.superMethod
      case clazz: ScTemplateDefinition => clazz.supers.headOption
      case _                           => None
    }

    superCommentOwner.foreach {
      case scalaDocOwner: ScDocCommentOwner =>
        scalaDocOwner.docComment.map { superComment =>
          buffer.append("<p>")
          val generator = new ScalaDocContentWithSectionsGenerator(superComment,  macroFinder)
          generator.generateContent(buffer)
          buffer.append("<br>")
        }
      case javaDocOwner =>
        val superContent = ScalaDocUtil.generateJavaDocInfoContentInner(javaDocOwner)
        superContent.foreach { content =>
          buffer.append("<p>")
          buffer.append(content)
          buffer.append("<br>")
        }
    }
  }

  private def innerContentText(element: PsiElement): String = {
    val buffer = StringBuilder.newBuilder
    visitNode(buffer, element)
    buffer.result
  }

  private def nodesText(elements: TraversableOnce[PsiElement]): String = {
    val buffer = StringBuilder.newBuilder
    elements.foreach(visitNode(buffer, _))
    buffer.result
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
    if (markupTag.contains("[[")) {
      buffer.append(generateLink(syntax))
    } else {
      markupTag.flatMap(markupTagToHtmlTag) match {
        case Some(htmlTag) =>
          buffer.append(s"<$htmlTag>")
          visitNodes(buffer, syntax.children.filter(isMarkupInner))
          buffer.append(s"</$htmlTag>")
        case None          =>
          visitNodes(buffer, syntax.children)
      }
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
    elementType match {
      case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS => // skip
      case ScalaDocTokenType.DOC_TAG_NAME                  => // skip
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN           => // skip
      case ScalaDocTokenType.DOC_INNER_CODE_TAG            => result.append("<pre><code>")
      case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG      => result.append("</code></pre>")
      case ScalaDocTokenType.DOC_COMMENT_DATA              => result.append(elementText)
      case ScalaDocTokenType.DOC_MACROS                    => result.append(macroValueSafe(macroFinder, elementText.stripPrefix("$")))
      case ScalaDocTokenType.DOC_WHITESPACE if elementText.contains("\n") =>
        // if it's trailing new line (new line between doc lines), do not add leading whitespaces
        // (in case the comment is intended far to the right), just add simple indent
        result.append("\n ")
      case _ => result.append(elementText)
    }
  }

  private def markupTagToHtmlTag(markupTag: String): Option[String] = markupTag match {
    case "__"  => Some("u")
    case "'''" => Some("b")
    case "''"  => Some("i")
    case "`"   => Some("tt")
    case ",,"  => Some("sub")
    case "^"   => Some("sup")
    case h if h.nonEmpty && h.forall(_ == '=') =>
      // TODO: looks like <h1>, <h2>... tags do not work properly in the platform,
      //  they are not rendered as actual headers
      Some(s"h${h.length}")
    case _ => None
  }
}

object ScalaDocContentWithSectionsGenerator {

  private val Log = Logger.getInstance(classOf[ScalaDocContentWithSectionsGenerator])

  def generate(
    buffer: StringBuilder,
    commentOwner: ScDocCommentOwner,
    comment: ScDocComment,
    rendered: Boolean
  ): Unit = {
    val generator = new ScalaDocContentWithSectionsGenerator(commentOwner, comment)
    generator.generate(buffer, rendered)
  }

  private def renderDefineTagChildElement(doc: MacroFinderImpl.ScDocCommentWithOwner, element: PsiElement): String = {
    // TODO: we do not support recursive macros, only 1 level
    val innerMacroFinder = MacroFinderDummy
    val generator = new ScalaDocContentWithSectionsGenerator(doc.comment, innerMacroFinder)
    generator.innerContentText(element)
  }

  private case class Section(title: String, content: String)

  // e.g. @throws Exception(value) condition(description)
  private case class ParamInfo(value: String, description: String)

  private case class PsiElementResolveResult(qualifiedName: String, shortestName: String)

  private def generatePsiElementLink(ref: ScStableCodeReference, context: PsiElement): String = {
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
        case companions@(Array(_: ScTypeDefinition, _: ScObject) | Array(_: ScObject, _: ScTypeDefinition)) =>
          val selectCompanion = refText.endsWith("$")
          if (selectCompanion)
            companions.find(_.element.isInstanceOf[ScObject])
          else
            companions.find(!_.element.isInstanceOf[ScObject])
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
}