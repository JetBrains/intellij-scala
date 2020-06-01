package org.jetbrains.plugins.scala.editor.documentationProvider

import java.lang.{StringBuilder => JStringBuilder}

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.editor.documentationProvider.ScaladocWikiProcessor.WikiProcessorResult
import org.jetbrains.plugins.scala.editor.documentationProvider.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.extensions.&&
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.{HtmlPsiUtils, PresentationUtil}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectContext

object ScalaDocGenerator {

  def generateDoc(elementWithDoc: PsiElement, originalElement: Option[PsiElement]): String = {
    val e = elementWithDoc.getNavigationElement

    implicit val projectContext: ProjectContext = e.projectContext
    implicit def typeRenderer: TypeRenderer = {
      val presentableContext = originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)
      _.urlText(presentableContext)
    }

    val builder = new HtmlBuilderWrapper
    import builder._

    def appendDefinitionSection(mainPart: => Unit): Unit = {
      append(DocumentationMarkup.DEFINITION_START)
      mainPart
      append(DocumentationMarkup.DEFINITION_END)
    }

    def appendDefinitionSectionWithComment(docOwner: PsiDocCommentOwner)(mainPart: => Unit): Unit = {
      appendDefinitionSection(mainPart)
      append(parseDocComment(docOwner))
    }

    def appendContainingClass(elem: ScMember): Unit =
      parseClassUrl(elem) match {
        case Some(psiLink) =>
          append(psiLink)
          appendNl()
        case _ =>
      }

    def appendDeclMainSection(element: PsiElement): Unit =
      appendDeclMainSection2(element, element)

    def appendDeclMainSection2(element: PsiElement, keywordOwner: PsiElement): Unit = {
      element match {
        case an: ScAnnotationsHolder =>
          val annotationsRendered = annotationsRenderer.renderAnnotations(an)
          append(annotationsRendered)
        case _ =>
      }

      //        val start = length

      element match {
        case m: ScModifierListOwner =>
          val renderer = new ModifiersRenderer(new AccessModifierRenderer(AccessQualifierRenderer.WithHtmlPsiLink))
          append(renderer.render(m))
        case _ =>
      }

      append(ScalaDocumentationUtils.getKeyword(keywordOwner))

      b {
        append(element match {
          case named: ScNamedElement => escapeHtml(named.name)
          case value: ScValueOrVariable => escapeHtml(value.declaredNames.head) // TODO
          case _ => "_"
        })
      }

      element match {
        case tpeParamOwner: ScTypeParametersOwner =>
          append(parseTypeParameters(tpeParamOwner))
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

    def appendTypeDef(typedef: ScTypeDefinition): Unit =
      appendDefinitionSectionWithComment(typedef) {
        val path = typedef.getPath
        if (path.nonEmpty) {
          append(path)
          appendNl()
        }
        appendDeclMainSection(typedef)
        val extendsListRendered = parseExtendsBlock(typedef.extendsBlock)
        if (extendsListRendered.nonEmpty) {
          appendNl()
          append(extendsListRendered)
        }
      }

    def appendFunction(fun: ScFunction): Unit =
      appendDefinitionSectionWithComment(fun) {
        appendContainingClass(fun)
        appendDeclMainSection(fun)
      }

    def appendTypeAlias(tpe: ScTypeAlias): Unit =
      appendDefinitionSectionWithComment(tpe) {
        appendContainingClass(tpe)
        appendDeclMainSection(tpe)
        tpe match {
          case definition: ScTypeAliasDefinition =>
            val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(psi.types.api.Any)
            append(s" = ${typeRenderer(tp)}")
          case _ =>
        }
      }

    def appendValOrVar(decl: ScValueOrVariable): Unit =
      appendDefinitionSectionWithComment(decl) {
        decl match {
          case decl: ScMember =>
            appendContainingClass(decl)
          case _ =>
        }
        appendDeclMainSection(decl)
      }

    def appendBindingPattern(pattern: ScBindingPattern): Unit =
      pattern.nameContext match {
        case (definition: ScValueOrVariable) && (_: ScPatternDefinition | _: ScVariableDefinition) =>
          appendDefinitionSectionWithComment(definition) {
            appendContainingClass(definition)
            appendDeclMainSection2(pattern, definition)
          }
        case _                           =>
      }

    // TODO: it should contain description of the parameter from the scaladoc
    def appendParameter(param: ScParameter): Unit =
      appendDefinitionSection {
        appendDeclMainSection(param)
      }

    withHtmlMarkup {
      e match {
        case typeDef: ScTypeDefinition => appendTypeDef(typeDef)
        case fun: ScFunction           => appendFunction(fun)
        case tpe: ScTypeAlias          => appendTypeAlias(tpe)
        case decl: ScValueOrVariable   => appendValOrVar(decl)
        case pattern: ScBindingPattern => appendBindingPattern(pattern)
        case param: ScParameter        => appendParameter(param)
        case _                         =>
      }
    }

    val result = builder.result()
    result
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

  private def parseClassUrl(elem: ScMember): Option[String]= {
    val clazz = elem.containingClass
    if (clazz == null) None
    else Some(HtmlPsiUtils.classFullLink(clazz))
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    // todo hyperlink identifiers in type bounds
    if (typeParameters.nonEmpty)
      escapeHtml(typeParameters.map(PresentationUtil.presentationStringForPsiElement(_)).mkString("[", ", ", "]"))
    else EmptyDoc
  }

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: TypeRenderer): String = {
    val buffer: StringBuilder = new StringBuilder()
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        buffer.append(typeToString(seq.head.`type`().getOrAny))
        if (seq.length > 1) {
          buffer.append("\n")
          for (i <- 1 until seq.length) {
            if (i > 1)
              buffer.append(" ")
            buffer.append("with ")
            buffer.append(typeToString(seq(i).`type`().getOrAny))
          }
        }
      case None =>
        if (elem.isUnderCaseClass) {
          buffer.append(HtmlPsiUtils.psiElementLink("scala.Product", "Product"))
        }
    }

    if (buffer.isEmpty) EmptyDoc
    else "extends " + buffer
  }

  // TODO: strange naming.. not "parse", it not only parses but also resolves base
  private def parseDocComment(potentialDocOwner: PsiDocCommentOwner): String =
    findActualComment(potentialDocOwner).fold(EmptyDoc) { case (docOwner, docComment, isInherited) =>
      parseDocComment(docOwner, docComment, isInherited)
    }

  private def findActualComment(docOwner: PsiDocCommentOwner): Option[(PsiDocCommentOwner, PsiDocComment, Boolean)] =
    docOwner.getDocComment match {
      case null =>
        superElementWithDocComment(docOwner) match {
          case Some((base, baseComment)) =>
            Some((base, baseComment, true))
          case _ =>
            None
        }
      case docComment =>
        Some((docOwner, docComment, false))
    }

  private def parseDocComment(
    docOwner: PsiDocCommentOwner,
    docComment: PsiDocComment,
    isInherited: Boolean
  ): String = {
    val commentParsed = docComment match {
      case scalaDoc: ScDocComment => generateScalaDocInfoContent(docOwner, scalaDoc)
      case _                      => generateJavaDocInfoContent(docOwner)
    }
    if (isInherited)
      wrapWithInheritedDescription(docOwner.containingClass)(commentParsed)
    else
      commentParsed
  }

  private def superElementWithDocComment(docOwner: PsiDocCommentOwner) =
    docOwner match {
      case method: PsiMethod => superMethodWithDocComment(method)
      case _                 => None
    }

  private def superMethodWithDocComment(method: PsiMethod): Option[(PsiMethod, PsiDocComment)] =
    method.superMethods.map(base => (base, base.getDocComment)).find(_._2 != null)

  def generateScalaDocInfoContent(
    docCommentOwner: PsiDocCommentOwner,
    docComment: ScDocComment
  ): String =
    prepareFakeJavaElementWithComment(docCommentOwner, docComment) match {
      case Some((javaElement, sections)) =>
        val javaDoc = generateJavaDocInfoContent(javaElement)
        val result = insertCustomSections(javaDoc, sections)
        result
      case _ => ""
    }

  def generateRenderedScalaDocContent(
    docCommentOwner: PsiDocCommentOwner,
    docComment: ScDocComment
  ): String =
    prepareFakeJavaElementWithComment(docCommentOwner, docComment) match {
      case Some((javaElement, sections)) =>
        val javaDoc = generateRenderedJavaDocInfo(javaElement)
        val result = insertCustomSections(javaDoc, sections)
        result
      case _ => ""
    }

  private def insertCustomSections(javaDoc: String, sections: Seq[ScaladocWikiProcessor.Section]): String = {
    import DocumentationMarkup._
    val sectionsEnd = javaDoc.indexOf(SECTIONS_END)
    if (sectionsEnd > -1)
      return insertCustomSections(javaDoc, sectionsEnd, sections, createSectionsTag = false)

    val contentEndTag = javaDoc.indexOf(CONTENT_END)
    if (contentEndTag > -1)
      return insertCustomSections(javaDoc, contentEndTag + CONTENT_END.length, sections, createSectionsTag = true)

    val definitionEndTag = javaDoc.indexOf(DEFINITION_END)
    if (definitionEndTag > -1)
      return insertCustomSections(javaDoc, definitionEndTag + DEFINITION_END.length, sections, createSectionsTag = true)

    // assuming that it's the impossible case and generated javadoc at least contains definition section
    ""
  }

  private def insertCustomSections(
    javadoc: String,
    index: Int,
    sections: Seq[ScaladocWikiProcessor.Section],
    createSectionsTag: Boolean
  ): String = {
    import DocumentationMarkup._
    val builder = new JStringBuilder
    builder.append(javadoc, 0, index)
    if (createSectionsTag)
      builder.append(SECTIONS_START)
    appendCustomSections(builder, sections)
    if (createSectionsTag)
      builder.append(SECTIONS_END)
    builder.append(javadoc, index, javadoc.length)
    builder.toString
  }

  private def appendCustomSections(output: JStringBuilder, sections: Seq[ScaladocWikiProcessor.Section]): Unit =
    sections.foreach { section =>
      import DocumentationMarkup._
      output
        .append(SECTION_HEADER_START)
        .append(section.title)
        .append(":")
        .append(SECTION_SEPARATOR)
        .append(section.content)
        .append(SECTION_END)
    }

  private def prepareFakeJavaElementWithComment(
    docCommentOwner: PsiDocCommentOwner,
    docComment: ScDocComment
  ): Option[(PsiDocCommentOwner, Seq[ScaladocWikiProcessor.Section])] = {
    val WikiProcessorResult(withReplacedWikiTags, sections) = ScaladocWikiProcessor.replaceWikiWithTags(docComment)
    createFakeJavaElement(docCommentOwner, withReplacedWikiTags).map((_, sections))
  }

  private def createFakeJavaElement(
    elem: PsiDocCommentOwner,
    docText: String
  ): Option[PsiDocCommentOwner] = {
    def getParams(fun: ScParameterOwner): String =
      fun.parameters.map(param => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")

    def getTypeParams(tParams: Seq[ScTypeParam]): String =
      if (tParams.isEmpty) ""
      else tParams.map(param => escapeHtml(param.name)).mkString("<", " , ", ">")

    val javaText = elem match {
      case clazz: ScClass =>
        s"""
           |class A {
           |$docText
           |public ${getTypeParams(clazz.typeParameters)}void f${getParams(clazz)}{
           |}""".stripMargin
      case typeAlias: ScTypeAlias =>
        s"""$docText
           |class A${getTypeParams(typeAlias.typeParameters)} {}""".stripMargin
      case _: ScTypeDefinition =>
        s"""$docText
           |class A {
           |}""".stripMargin
      case f: ScFunction =>
        s"""class A {
           |$docText
           |public ${getTypeParams(f.typeParameters)}int f${getParams(f)} {}
           |}""".stripMargin
      case m: PsiMethod =>
        s"""class A {
           |${m.getText}
           |}""".stripMargin
      case _ =>
        s"""$docText
           |class A""".stripMargin
    }

    val javaDummyFile = createDummyJavaFile(javaText, elem.getProject)

    val clazz = javaDummyFile.getClasses.head
    // not using getAllMethods to avoid accessing indexes (and thus work in dump mode)
    elem match {
      case _: ScFunction | _: ScClass | _: PsiMethod => clazz.children.findByType[PsiMethod]
      case _                                         => Some(clazz)
    }
  }

  private def createDummyJavaFile(text: String, project: Project): PsiJavaFile =
    PsiFileFactory.getInstance(project).createFileFromText("dummy", StdFileTypes.JAVA, text).asInstanceOf[PsiJavaFile]

  private def generateJavaDocInfoContent(element: PsiElement): String = {
    val javadoc = generateJavaDocInfo(element)
    val javadocContent = extractJavaDocContent(javadoc)
    javadocContent
  }

  private def generateJavaDocInfo(element: PsiElement): String = {
    val builder = new java.lang.StringBuilder()
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateDocInfoCore(builder, false)
    builder.toString
  }

  private def generateRenderedJavaDocInfo(element: PsiElement): String = {
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateRenderedDocInfo
  }

  // TODO: this is far from perfect to rely on text... =(
  //  dive deep into Javadoc generation and implement in a more safe and structural way
  private def extractJavaDocContent(javadoc: String): String = {
    val contentStartIdx = javadoc.indexOf(DocumentationMarkup.CONTENT_START) match {
      case -1 => javadoc.indexOf(DocumentationMarkup.SECTIONS_START)
      case idx => idx
    }
    if (contentStartIdx > 0) javadoc.substring(contentStartIdx)
    else javadoc
  }

  private def wrapWithInheritedDescription(clazz: PsiClass)(text: String): String = {
    val prefix =
      s"""${DocumentationMarkup.CONTENT_START}
         |<b>Description copied from class: </b>
         |${HtmlPsiUtils.psiElementLink(clazz.qualifiedName, clazz.name)}
         |${DocumentationMarkup.CONTENT_END}""".stripMargin
    prefix + text
  }
}
