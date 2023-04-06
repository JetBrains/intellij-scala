package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi._
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.extensions.{&, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.HtmlPsiUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TextEscaper.Html
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, TypePresentationContext}
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
  private implicit val typeRenderer: TypeRenderer = {
    val presentableContext = originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)
    _.urlText(presentableContext)
  }

  private def generate(): Unit =
    elementWithDoc match {
      case typeDef: ScTypeDefinition => appendTypeDef(typeDef)
      case fun: ScFunction           => appendFunction(fun)
      case tpe: ScTypeAlias          => appendTypeAlias(tpe)
      case decl: ScValueOrVariable   => appendValOrVar(decl)
      case pattern: ScBindingPattern => appendBindingPattern(pattern)
      case param: ScParameter        => appendParameter(param)
      case _                         =>
    }

  private def appendDefinitionSection(mainPart: => Unit): Unit = {
    builder.append(DocumentationMarkup.DEFINITION_START)
    mainPart
    builder.append(DocumentationMarkup.DEFINITION_END)
  }

  private def appendContainingClass(elem: ScMember): Unit =
    containingClassHyperLink(elem).foreach { psiLink =>
      builder.append(psiLink).append("\n")
    }

  private def appendDeclMainSection(element: PsiElement): Unit =
    appendDeclMainSection2(element, element)

  private def appendDeclMainSection2(element: PsiElement, keywordOwner: PsiElement): Unit = {
    import builder._

    element match {
      case an: ScAnnotationsHolder =>
        val annotationsRendered = annotationsRenderer.renderAnnotations(an)
        append(annotationsRendered)
      case _ =>
    }

    element match {
      case m: ScModifierListOwner =>
        val renderer = new ModifiersRenderer(new AccessModifierRenderer(AccessQualifierRenderer.WithHtmlPsiLink))
        val rendered = renderer.render(m)
        builder.keyword { append(rendered) }
      case _ =>
    }

    val keyword = ScalaDocumentationUtils.getKeyword(keywordOwner)
    builder.keyword { append(keyword) }

    builder.b {
      append(element match {
        case named: ScNamedElement => escapeHtml(named.name)
        case value: ScValueOrVariable => escapeHtml(value.declaredNames.head) // TODO
        case _ => "_"
      })
    }

    element match {
      case tpeParamOwner: ScTypeParametersOwner =>
        val renderer = new TypeParamsRenderer(typeRenderer, new TypeBoundsRenderer(Html))
        val rendered = renderer.renderParams(tpeParamOwner)
        builder.keyword { append(rendered) }
      case _ =>
    }

    element match {
      case params: ScParameterOwner =>
        val renderer = definitionParamsRenderer(typeRenderer)
        // TODO: since SCL-13777 spaces are effectively not used! cause we remove all new lines and spaces after rendering
        //  review SCL-13777, maybe we should improve formatting of large classes
        //val spaces = length - start - 7
        val paramsRendered = renderer.renderClauses(params).replaceAll("\n\\s*", "")
        append(paramsRendered)
      case _ =>
    }

    val typeAnnotation = element match {
      case _: ScObject              => "" // ignore, object doesn't need type annotation
      case typed: ScTypedDefinition => typeAnnotationRenderer.render(typed)
      case typed: ScValueOrVariable => typeAnnotationRenderer.render(typed)
      case _                        => ""
    }
    append(typeAnnotation)
  }

  private def appendTypeDef(typedef: ScTypeDefinition): Unit =
    appendDefinitionSection {
      val path = typedef.getPath
      if (path.nonEmpty) {
        builder.append(s"<icon src=\"AllIcons.Nodes.Package\"/> ")
        builder.append(path)
        builder.append("<br/>\n")
      }
      appendDeclMainSection(typedef)
      val extendsListRendered = parseExtendsBlock(typedef.extendsBlock)
      if (extendsListRendered.nonEmpty) {
        builder.append("\n")
        builder.append(extendsListRendered)
      }
    }

  private def appendFunction(fun: ScFunction): Unit =
    appendDefinitionSection {
      appendContainingClass(fun)
      appendDeclMainSection(fun)
    }

  private def appendTypeAlias(tpe: ScTypeAlias): Unit =
    appendDefinitionSection {
      appendContainingClass(tpe)
      appendDeclMainSection(tpe)
      tpe match {
        case definition: ScTypeAliasDefinition =>
          val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(psi.types.api.Any)
          builder.append(s" = ${typeRenderer(tp)}")
        case _ =>
      }
    }

  private def appendValOrVar(decl: ScValueOrVariable): Unit =
    appendDefinitionSection {
      decl match {
        case decl: ScMember =>
          appendContainingClass(decl)
        case _ =>
      }
      appendDeclMainSection(decl)
    }

  private def appendBindingPattern(pattern: ScBindingPattern): Unit =
    pattern.nameContext match {
      case (definition: ScValueOrVariable) & (_: ScPatternDefinition | _: ScVariableDefinition) =>
        appendDefinitionSection {
          appendContainingClass(definition)
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

  private def containingClassHyperLink(elem: ScMember): Option[String] = {
    val clazz = elem.containingClass
    if (clazz == null) None
    else HtmlPsiUtils.classFullLinkSafe(clazz, defLinkHighlight = false)
  }

  private def typeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorateOptions.DecorateAll)

  private def annotationsRenderer(implicit typeRenderer: TypeRenderer): AnnotationsRendererLike =
    new AnnotationsRenderer(typeRenderer, "\n", TextEscaper.Html) {
      override def shouldSkipArguments(annotationType: ScType, arguments: Seq[ScExpression]): Boolean =
        arguments.isEmpty || isThrowsAnnotationConstructor(annotationType, arguments)

      // see SCL-17608
      private def isThrowsAnnotationConstructor(annotationType: ScType, arguments: Seq[ScExpression]): Boolean =
        if (arguments.size == 1) {
          //assuming that @throws annotation has single constructor with parametrized type which accepts java.lang.Class
          annotationType.extractClass.exists { clazz =>
            clazz.qualifiedName == "scala.throws" &&
              arguments.head.`type`().exists(_.isInstanceOf[ScParameterizedType])
          }
        } else false
    }

  private def definitionParamsRenderer(implicit typeRenderer: TypeRenderer): ParametersRenderer = {
    val parameterRenderer = new ParameterRenderer(
      typeRenderer,
      ModifiersRenderer.WithHtmlPsiLink,
      typeAnnotationRenderer(typeRenderer),
      TextEscaper.Html,
      withMemberModifiers = false,
      withAnnotations = true
    )
    new ParametersRenderer(
      parameterRenderer,
      renderImplicitModifier = true,
      clausesSeparator = "",
    )
  }

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
            for (i <- 1 until seq.length) {
              if (i > 1) {
                buffer.append(if (seq.length > 3) "<br/>" else " ")
              }
              buffer.keyword { buffer.append("with ") }
              buffer.append(typeToString(seq(i).`type`().getOrAny))
            }
          }
        }
      case None =>
        if (elem.isUnderCaseClass) {
          buffer.append(HtmlPsiUtils.psiElementLink("scala.Product", "Product"))
        }
    }

    val result = buffer.toString.trim
    if (result.isEmpty)
      EmptyDoc
    else {
      val buffer2 = new StringBuilder
      buffer2.keyword { buffer2.append("extends ") }
      buffer2.append(result)
      buffer2.toString
    }
  }
}
