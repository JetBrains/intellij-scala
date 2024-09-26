package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationMarkup.BOTTOM_ELEMENT
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.editor.documentationProvider.renderers.{ScalaDocAnnotationRenderer, ScalaDocParametersRenderer, ScalaDocTypeParamsRenderer, ScalaDocTypeRenderer, WithHtmlPsiLink}
import org.jetbrains.plugins.scala.extensions.{&, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition.DesugaredTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScMember, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.project.ProjectContext

object ScalaDocDefinitionGenerator {

  def generate(
    builder: StringBuilder,
    // it's not PsiDocCommentOwner or ScDocCommentOwner due to ScBindingPattern and ScParameter do not inherit those
    elementWithDoc: PsiElement,
    originalElement: Option[PsiElement]
  ): Unit =
    new ScalaDocDefinitionGenerator(builder, elementWithDoc, originalElement).generate()
}

private class ScalaDocDefinitionGenerator private(
  builder: StringBuilder,
  // it's not PsiDocCommentOwner or ScDocCommentOwner due to ScBindingPattern and ScParameter do not inherit those
  elementWithDoc: PsiElement,
  originalElement: Option[PsiElement],
) {

  private implicit val projectContext: ProjectContext = elementWithDoc.projectContext
  private implicit val typeRenderer: TypeRenderer = ScalaDocTypeRenderer(originalElement)

  private def generate(): Unit =
    elementWithDoc match {
      case DesugaredTypeDefinition(gvn) => appendGivenDef(gvn)
      case gvn: ScGivenDefinition       => appendGivenDef(gvn)
      case typeDef: ScTypeDefinition    => appendTypeDef(typeDef)
      case fun: ScFunction              => appendFunction(fun)
      case tpe: ScTypeAlias             => appendTypeAlias(tpe)
      case decl: ScValueOrVariable      => appendValOrVar(decl)
      case pattern: ScBindingPattern    => appendBindingPattern(pattern)
      case param: ScParameter           => appendParameter(param)
      case _                            =>
    }

  private def appendDefinitionSection(mainPart: => Unit): Unit = {
    builder.append(DocumentationMarkup.DEFINITION_START)
    mainPart
    builder.append(DocumentationMarkup.DEFINITION_END)
  }

  private def appendContainerInfoSection(member: ScMember): Unit = {
    val containerInfo = generateContainerInfoSection(member)
    containerInfo.foreach { section =>
      section.appendTo(builder.underlying)
      // This extra new line doesn't change anything visually in HTML as it's added between <div> tags.
      // But makes it more convenient to write test data in tests.
      builder.append("\n")
    }
  }

  private def generateContainerInfoSection(member: ScMember): Option[HtmlChunk.Element] =
    for {
      (container, fqn) <- getContainerWithQualifiedName(member)
      iconFqn <- ContainerIconUtils.getContainerIconFqn(container)
      psiLink = HtmlPsiUtils.psiElementLink(fqn = fqn, label = fqn)
    } yield {
      generateContainerInfoSection(iconFqn, psiLink)
    }

  private def generateContainerInfoSection(containerIconFqn: String, @NlsSafe containerLink: String): HtmlChunk.Element =
    BOTTOM_ELEMENT.children(
      HtmlChunk.tag("icon").attr("src", containerIconFqn),
      HtmlChunk.nbsp,
      HtmlChunk.raw(containerLink)
    )

  private def getContainerWithQualifiedName(member: ScMember) = {
    //handle members of a type definition
    val containerTypeDefinition = member match {
      //containingClass for enum returns a synthetic object, but we need the synthetic container
      case ec: ScEnumCase => Option(ec.enumParent)
      case _ => Option(member.containingClass)
    }

    //handle top-level definitions
    lazy val containerPackage: Option[ScPackaging] = {
      val potentiallyTopLevelMember = member match {
        case f: ScFunction => f.extensionMethodOwner.getOrElse(f)
        case _ => member
      }
      Option(PsiTreeUtil.getStubOrPsiParent(potentiallyTopLevelMember)).filterByType[ScPackaging]
    }

    val container = containerTypeDefinition.orElse(containerPackage)
    container.map {
      case c: ScTemplateDefinition => (c, c.qualifiedName)
      case p: ScPackaging => (p, p.fullPackageName)
    }
  }

  private def appendDeclMainSection(element: PsiElement): Unit =
    appendDeclMainSection2(element, element)

  private def appendDeclMainSection2(element: PsiElement, keywordOwner: PsiElement): Unit = {
    element match {
      case an: ScAnnotationsHolder =>
        val annotationsRendered = annotationsRenderer.renderAnnotations(an)
        if (annotationsRendered.nonEmpty) builder.append(annotationsRendered)
      case _ =>
    }

    (element, keywordOwner) match {
      case (m: ScModifierListOwner, _) =>
        val modifiersRendered = WithHtmlPsiLink.modifiersRenderer.render(m)
        if (modifiersRendered.nonEmpty) builder.append(modifiersRendered)
      case (_, m: ScModifierListOwner) =>
        val modifiersRendered = WithHtmlPsiLink.modifiersRenderer.render(m)
        if (modifiersRendered.nonEmpty) builder.append(modifiersRendered)
      case _ =>
    }

    val (keyword, attrKey) = ScalaDocumentationUtils.getKeywordAndTextAttributesKey(keywordOwner)

    if (keyword.nonEmpty) builder.appendKeyword(keyword).append(" ")

    element match {
      case named: ScNamedElement =>
        val name = escapeHtml4(named.name)
        builder.appendAs(name, attrKey)
      case value: ScValueOrVariable if value.declaredNames.nonEmpty =>
        val name = escapeHtml4(value.declaredNames.head)
        builder.appendAs(name, attrKey)
      case _ =>
        builder.append("_")
    }

    element match {
      case tpeParamOwner: ScTypeParametersOwner =>
        val renderedTypeParams = typeParamsRenderer.renderParams(tpeParamOwner)
        if (renderedTypeParams.nonEmpty) builder.append(renderedTypeParams)
      case _ =>
    }

    element match {
      case params: ScParameterOwner =>
        // TODO: since SCL-13777 spaces are effectively not used! cause we remove all new lines and spaces after rendering
        //  review SCL-13777, maybe we should improve formatting of large classes
        //val spaces = length - start - 7
        val paramsRendered = definitionParamsRenderer.renderClauses(params).replaceAll("\n\\s*", "")
        if (paramsRendered.nonEmpty) builder.append(paramsRendered)
      case _ =>
    }

    element match {
      case DesugaredTypeDefinition(gvn) => typeAnnotationRenderer.render(builder, gvn)
      case _: ScObject                  => // ignore, an object doesn't need type annotation
      case typed: ScTypedDefinition     => typeAnnotationRenderer.render(builder, typed)
      case typed: ScValueOrVariable     => typeAnnotationRenderer.render(builder, typed)
      case _                            =>
    }
  }

  private def appendTypeDef(typedef: ScTypeDefinition): Unit = {
    appendContainerInfoSection(typedef)
    appendDefinitionSection {
      appendDeclMainSection(typedef)
      val extendsListRendered = parseExtendsBlock(typedef.extendsBlock)
      if (extendsListRendered.nonEmpty) {
        builder.append("\n")
        builder.append(extendsListRendered)
      }
    }
  }

  private def appendGivenDef(givenDef: ScGivenDefinition): Unit = {
    appendContainerInfoSection(givenDef)
    appendDefinitionSection {
      appendDeclMainSection(givenDef)
    }
  }

  private def appendFunction(fun: ScFunction): Unit = {
    appendContainerInfoSection(fun)
    appendDefinitionSection {
      appendDeclMainSection(fun)
    }
  }

  private def appendTypeAlias(tpe: ScTypeAlias): Unit = {
    appendContainerInfoSection(tpe)
    appendDefinitionSection {
      appendDeclMainSection(tpe)
      tpe match {
        case definition: ScTypeAliasDefinition =>
          val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(psi.types.api.Any)
          builder.append(s" = ${typeRenderer(tp)}")
        case _ =>
      }
    }
  }

  private def appendValOrVar(decl: ScValueOrVariable): Unit = {
    appendContainerInfoSection(decl)
    appendDefinitionSection {
      appendDeclMainSection(decl)
    }
  }

  private def appendBindingPattern(pattern: ScBindingPattern): Unit =
    pattern.nameContext match {
      case (definition: ScValueOrVariable) & (_: ScPatternDefinition | _: ScVariableDefinition) =>
        appendContainerInfoSection(definition)
        appendDefinitionSection {
          appendDeclMainSection2(pattern, definition)
        }
      case _  =>
        appendDefinitionSection {
          builder.append(ScalaEditorBundle.message("section.pattern"))
          builder.append(' ')
          appendDeclMainSection(pattern)
        }
    }

  // TODO: it should contain description of the parameter from the scaladoc
  private def appendParameter(param: ScParameter): Unit =
    appendDefinitionSection {
      appendDeclMainSection(param)
    }

  // UTILS

  private lazy val typeAnnotationRenderer: TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAllMinimized)
  private lazy val annotationsTypeRenderer =
    ScalaDocTypeRenderer.forAnnotations(originalElement)
  private lazy val annotationsRenderer =
    new ScalaDocAnnotationRenderer(annotationsTypeRenderer)
  private lazy val typeParamsRenderer =
    new ScalaDocTypeParamsRenderer(typeRenderer)
  private lazy val definitionParamsRenderer: ParametersRenderer =
    ScalaDocParametersRenderer(typeRenderer, typeAnnotationRenderer)

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: TypeRenderer): String = {
    val buffer = new StringBuilder
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        if (seq.nonEmpty) {
          buffer.append(typeToString(seq.head.`type`().getOrAny))
          if (seq.length > 1) {
            buffer.append("\n")
            val typeElementsSeparator = if (seq.length > 3) NewLineSeparatorInDefinitionSection else " "
            for (i <- 1 until seq.length) {
              if (i > 1) buffer.append(typeElementsSeparator)
              buffer
                .appendKeyword("with")
                .append(" ")
                .append(typeToString(seq(i).`type`().getOrAny))
            }
          }
        }
      case _ =>
    }

    val result = buffer.toString.trim
    if (result.isEmpty)
      EmptyDoc
    else
      (new StringBuilder)
        .appendKeyword("extends")
        .append(" ")
        .append(result)
        .toString
  }
}
