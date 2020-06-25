package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentWithSectionsGenerator.{ParamInfo, Section}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.mutable.ArrayBuffer

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
 */
private class ScalaDocContentWithSectionsGenerator(
  comment: ScDocComment,
  macroFinder: MacroFinder,
  rendered: Boolean
) {

  private val resolveContext: PsiElement = comment

  private def newContentGenerator: ScalaDocContentGenerator =
    new ScalaDocContentGenerator(resolveContext, macroFinder, rendered)

  def this(
    commentOwner: ScDocCommentOwner,
    comment: ScDocComment,
    rendered: Boolean
  ) = this(
    comment,
    new MacroFinderImpl(commentOwner, element => {
      // TODO: for now we do not support recursive macros, only 1 level
      val generator = new ScalaDocContentGenerator(comment, MacroFinderDummy, rendered)
      generator.nodeText(element)
    }),
    rendered
  )

  def generate(
    buffer: StringBuilder
  ): Unit = {

    val tags: Seq[ScDocTag] = comment.tags

    buffer.append(DocumentationMarkup.CONTENT_START)
    newContentGenerator.appendCommentDescription(buffer, comment)

    val inheritDocTagOpt = tags.find(_.name == MyScaladocParsing.INHERITDOC_TAG)
    inheritDocTagOpt.foreach { inheritDocTag =>
      addInheritedDocText(buffer)
      newContentGenerator.appendNodeText(buffer, inheritDocTag)
    }
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
    val result = matchingTags.map { tag =>
      val sectionContent = newContentGenerator.nodesText(tag.children)
      Section(sectionTitle, sectionContent.trim)
    }

    result
  }

  private def prepareParamsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val paramTags = tags.filter(_.name == MyScaladocParsing.PARAM_TAG)
    val paramTagsInfo = paramTags.flatMap(parameterInfo)
    if (paramTagsInfo.nonEmpty) {
      val content = parameterInfosText(paramTagsInfo)
      Some(Section("Params:", content))
    } else None
  }

  private def prepareTypeParamsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val typeParamTags = tags.filter(_.name == MyScaladocParsing.TYPE_PARAM_TAG)
    val typeParamTagsInfo = typeParamTags.flatMap(parameterInfo)
    if (typeParamTagsInfo.nonEmpty) {
      val content = parameterInfosText(typeParamTagsInfo)
      Some(Section("Type parameters:", content))
    } else None
  }

  private def prepareReturnsSection(tags: Seq[ScDocTag]): Option[Section] = {
    // TODO: if there is inherited doc, get return description from there
    val returnTag = tags.find(_.name == MyScaladocParsing.RETURN_TAG)
    returnTag.map(newContentGenerator.nodeText(_)).map(Section("Returns:", _))
  }

  private def prepareThrowsSection(tags: Seq[ScDocTag]): Option[Section] = {
    val throwTags      = tags.filter(_.name == MyScaladocParsing.THROWS_TAG)
    val throwTagsInfos = throwTags.flatMap(throwsInfo)
    if (throwTagsInfos.nonEmpty) {
      val content = parameterInfosText(throwTagsInfos)
      Some(Section("Throws:", content))
    } else None
  }

  private def parameterInfo(tag: ScDocTag): Option[ParamInfo] =
    tag.children.findByType[ScDocTagValue].map { tagValue =>
      val tagDescription = newContentGenerator.nodesText(tagValue.nextSiblings)
      ParamInfo(tagValue.getText, tagDescription)
    }

  private def throwsInfo(tag: ScDocTag): Option[ParamInfo] = {
    val exceptionRef = tag.children.findByType[ScStableCodeReference]
    exceptionRef.map { ref =>
      val value = ScalaDocContentGenerator.generatePsiElementLink(ref, resolveContext)
      val description = newContentGenerator.nodesText(ref.nextSiblings)
      ParamInfo(value, description)
    }
  }

  private def parameterInfosText(infos: Seq[ParamInfo]): String =
    infos.map(p => s"${p.value} &ndash; ${p.description.trim}").mkString("<p>")

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
          newContentGenerator.appendCommentDescription(buffer, superComment)
          buffer.append("<p>")
        }
      case javaDocOwner =>
        val superContent = ScalaDocUtil.generateJavaDocInfoContentInner(javaDocOwner)
        superContent.foreach { content =>
          buffer.append("<p>")
          buffer.append(content)
          buffer.append("<p>")
        }
    }
  }
}

object ScalaDocContentWithSectionsGenerator {

  private case class Section(title: String, content: String)
  // e.g. @throws Exception(value) condition(description)
  private case class ParamInfo(value: String, description: String)
}