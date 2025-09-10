package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentWithSectionsGenerator.{ParamInfo, Section}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.immutable.ArraySeq

/**
 * @note for the current moment (8 June 2020) there is no any Scala Doc specification.<br>
 *       From the gitter channel [[https://gitter.im/scala/contributors?at=5eda0c937da67d06faf2e43e]]:<br>
 *       "I'm pretty sure that no such (spec) thing exists if you want to try and extract one yourself,
 *       the main place to look would be [[scala.tools.nsc.doc.base.CommentFactoryBase]]"
 * @todo remove maximum common indentation from code examples { { { }}} not to shift it far to the right
 * @todo unify with description from https://docs.scala-lang.org/overviews/scaladoc/for-library-authors.html#markup<br>
 *       `Comment Inheritance - Implicit
 *       If a comment is not provided for an entity at the current inheritance level, but is supplied for the overridden entity at a higher level
 *       in the inheritance hierarchy, the comment from the super-class will be used.
 *       Likewise if @param, @tparam, @return and other entity tags are omitted but available from a superclass, those comments will be used.`
 * @todo some kind of JavaDocInfoGenerator#generateSuperMethodsSection when NOT in editor render mode
 */
private class ScalaDocContentWithSectionsGenerator(
  comment: ScDocComment,
  macroFinder: MacroFinder,
  rendered: Boolean
) {

  private val resolveContext: PsiElement = comment

  private def newContentGenerator: ScalaDocContentGenerator =
    new ScalaDocContentGenerator(comment, macroFinder)

  def this(
    commentOwner: ScDocCommentOwner,
    comment: ScDocComment,
    rendered: Boolean // todo: does it work in DumbMode, maybe disable macro in render mode?
  ) = this(
    comment,
    new MacroFinderImpl(commentOwner, rendered),
    rendered
  )

  def generate(buffer: StringBuilder): Unit = {
    val tags: Seq[ScDocTag] = comment.tags

    appendContentSection(buffer, tags)

    val sections = buildSections(tags)
    if (sections.nonEmpty) {
      buffer.append(DocumentationMarkup.SECTIONS_START)
      appendSections(sections, buffer)
      buffer.append(DocumentationMarkup.SECTIONS_END)
    }
  }

  def generateForParam(buffer: StringBuilder, param: ScParameter): Unit =
    comment.tags
      .find(tag => tag.name == MyScaladocParsing.TagNames.Param && tag.getValueElement.textMatches(param.name))
      .foreach { tag =>
        buffer.append(DocumentationMarkup.SECTIONS_START)
        newContentGenerator.appendTagDescriptionText(buffer, tag)
        buffer.append(DocumentationMarkup.SECTIONS_END)
      }

  /**
   * @todo there are currently so much logic in different places to avoid adding `<p>` in the very beginning of Content
   *       section (in in-editor render mode it adds extra visual indent in the beginning)
   *       first non-empty content can come from too many places:
   *       - comment description
   *       - inherited comment description via @inheritdoc (if own comment is empty)
   *       - @inheritdoc body (if inherited comment is empty. Yes it's a rare case, but still..)
   *
   *       Lets maybe move this peace of logic to some custom buffer instead of StringBuilder
   *       The buffer can also track whether "CONTENT_START" & "CONTENT_END" should be added.
   *       It can contain some kinda states begin -> definition -> content -> sections -> end
   *
   * @todo we could avoid many hacks making whole rendering pipeline more functional:
   *       rendering all around the places the parts/sub-parts to Strings, holding them in memory
   *       instead of rendering directly passed StringBuilder.
   *       But we should not forget about GC... in big documents there can be quite a lot of comments and if in-editor
   *       doc rendering is enabled it can consume quite a lot of CPU circles.
   *       It would be nice to investigate the impact on performance in more details in 2020.3 release cycle.
   */
  private def appendContentSection(buffer: StringBuilder, tags: Seq[ScDocTag]) = {
    var contentStartAdded = false
    def ensureContentStartAdded(): Unit =
      if (!contentStartAdded) {
        buffer.append(DocumentationMarkup.CONTENT_START)
        contentStartAdded = true
      }

    val descriptionParts = comment.descriptionParts
    val hasOwnDescription = descriptionParts.nonEmpty
    if (hasOwnDescription) {
      ensureContentStartAdded()
      newContentGenerator.appendDescriptionParts(buffer, descriptionParts)
    }

    tags.find(_.name == MyScaladocParsing.TagNames.Inheritdoc) match {
      case Some(inheritDocTag) =>
        ensureContentStartAdded()
        val added = addInheritedDocText(buffer, hasOwnDescription)
        if (added || hasOwnDescription)
          buffer.append("<p>")
        newContentGenerator.appendTagDescriptionText(buffer, inheritDocTag)
      case _ =>
    }

    if (contentStartAdded)
      buffer.append(DocumentationMarkup.CONTENT_END)
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
    val sectionsBuilder = ArraySeq.newBuilder[Section]

    sectionsBuilder ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.Deprecated, ScalaEditorBundle.message("scaladoc.section.deprecated"))

    val paramsSection     = prepareParamsSection(tags)
    val typeParamsSection = prepareTypeParamsSection(tags)
    val returnsSection    = prepareReturnsSection(tags)
    val throwsSection     = prepareThrowsSection(tags)

    sectionsBuilder ++=
      paramsSection ++=
      typeParamsSection ++=
      returnsSection ++=
      throwsSection

    sectionsBuilder ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.Note, ScalaEditorBundle.message("scaladoc.section.note")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.Example, ScalaEditorBundle.message("scaladoc.section.example")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.See, ScalaEditorBundle.message("scaladoc.section.see.also")) ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.Since, ScalaEditorBundle.message("scaladoc.section.since"))

    // trying to be consistent with Java
    // search for "rendered" parameter usage in com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
    // current java tags order:
    // generateApiSection(buffer, docComment);
    // generateDeprecatedSection(buffer, docComment);
    // generateSinceSection(buffer, docComment);
    // generateSeeAlsoSection(buffer, docComment);
    // generateAuthorAndVersionSections(buffer, comment); (IN RENDERED)
    // generateRecordParametersSection(buffer, aClass, comment);
    // generateTypeParametersSection(buffer, aClass, rendered);
    if (rendered) {
      sectionsBuilder ++=
        prepareSimpleSections(tags, MyScaladocParsing.TagNames.Author, ScalaEditorBundle.message("scaladoc.section.author")) ++=
        prepareSimpleSections(tags, MyScaladocParsing.TagNames.Version, ScalaEditorBundle.message("scaladoc.section.version"))
    }

    sectionsBuilder ++=
      prepareSimpleSections(tags, MyScaladocParsing.TagNames.Todo, ScalaEditorBundle.message("scaladoc.section.todo"))

    sectionsBuilder.result()
  }

  private def prepareSimpleSections(tags: Seq[ScDocTag], tagName: String, @Nls sectionTitle: String): Seq[Section] = {
    val matchingTags = tags.filter(_.name == tagName)
    val result = matchingTags.map { (tag: ScDocTag) =>
      val sectionContent = newContentGenerator.tagDescriptionText(tag)
      Section(sectionTitle, sectionContent.trim)
    }

    result
  }

  private def prepareParamsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val paramTags = tags.filter(_.name == MyScaladocParsing.TagNames.Param)
    val paramTagsInfo = paramTags.flatMap(parameterInfo)
    if (paramTagsInfo.nonEmpty) {
      val content = parameterInfosText(paramTagsInfo)
      Some(Section(ScalaEditorBundle.message("section.title.params"), content))
    } else None
  }

  private def prepareTypeParamsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val typeParamTags = tags.filter(_.name == MyScaladocParsing.TagNames.TypeParam)
    val typeParamTagsInfo = typeParamTags.flatMap(parameterInfo)
    if (typeParamTagsInfo.nonEmpty) {
      val content = parameterInfosText(typeParamTagsInfo)
      Some(Section(ScalaEditorBundle.message("section.title.type.parameters"), content))
    } else None
  }

  private def prepareReturnsSection(tags: Seq[ScDocTag]): Option[Section] = {
    // TODO: if there is inherited doc, get return description from there
    val returnTag = tags.find(_.name == MyScaladocParsing.TagNames.Return)
    returnTag.map(newContentGenerator.tagDescriptionText).map(Section(ScalaEditorBundle.message("section.title.returns"), _))
  }

  private def prepareThrowsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val throwTags      = tags.filter(_.name == MyScaladocParsing.TagNames.Throws)
    val throwTagsInfos = throwTags.flatMap(throwsInfo)
    if (throwTagsInfos.nonEmpty) {
      val content = parameterInfosText(throwTagsInfos)
      Some(Section(ScalaEditorBundle.message("section.title.throws"), content))
    } else None
  }

  /**
   * Handles @param and @tparam tags
   */
  private def parameterInfo(paramTag: ScDocTag): Option[ParamInfo] = {
    val paramName = Option(paramTag.getValueElement)
    paramName.map { paramName =>
      val paramNameRendered = s"<code>${paramName.getText}</code>"
      val tagDescription = newContentGenerator.tagDescriptionText(paramTag)
      ParamInfo(paramNameRendered, tagDescription)
    }
  }

  private def throwsInfo(tag: ScDocTag): Option[ParamInfo] = {
    val exceptionRef = tag.children.findByType[ScStableCodeReference]
    exceptionRef.map { ref =>
      val value = ScalaDocContentGenerator.generatePsiElementLink(ref, resolveContext)
      val description = newContentGenerator.tagDescriptionText(tag)
      ParamInfo(value, description)
    }
  }

  private def parameterInfosText(infos: Seq[ParamInfo]): String =
    infos.map(p => s"${p.displayedValue} &ndash; ${p.description.trim}").mkString("<p>")

  /** @return true - if some content was added from inherited doc<br>
   *          false - otherwise */
  private def addInheritedDocText(buffer: StringBuilder, hasOwnDescription: Boolean): Boolean = try {
    val superCommentOwner: Option[PsiDocCommentOwner] = comment.getOwner match {
      case fun: ScFunction             => fun.superMethod
      case clazz: ScTemplateDefinition => clazz.supers.headOption
      case _                           => None
    }

    // in case inherited class is in jar file we need to use sources for it
    superCommentOwner.fold(false) {
      case scalaDocOwner: ScDocCommentOwner =>
        scalaDocOwner.docComment match {
          case Some(superComment) =>
            if (hasOwnDescription)
              buffer.append("<p>")
            val parts = superComment.descriptionParts
            newContentGenerator.appendDescriptionParts(buffer, superComment.descriptionParts)
            parts.nonEmpty
          case _ =>
            false
        }
      case javaDocOwner: PsiDocCommentOwner =>
        ScalaDocUtil.generateJavaDocInfoContentInner(javaDocOwner) match {
          case Some(superContent) =>
            if (hasOwnDescription)
              buffer.append("<p>")
            buffer.append(superContent)
            true
          case None   =>
            false
        }
      case _ =>
        false
    }
  } catch {
    case _: IndexNotReadyException =>
      if (hasOwnDescription)
        buffer.append("<p>")
      buffer.append(ScalaEditorBundle.message("cannot.render.inherited.documentation.during.indexing"))
      true
  }
}

object ScalaDocContentWithSectionsGenerator {
  private case class Section(@Nls title: String, content: String)

  /**
   * Describes @param, @tparam and @throws tag
   *
   * @param displayedValue the displayed value of the parameter name or exception link (it can be wrapped with extra html tags)
   * @param description    parameter description
   * @example `@throws Exception some condition`<br>
   *          Here name ~ "Exception" and description ~ "some condition"
   */
  private case class ParamInfo(displayedValue: String, @Nls description: String)
}