package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.SuperMethodsSearch
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScAnnotation, ScAnnotationsHolder, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDocCommentOwner, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectContext

object ScalaDocGenerator {

  def generateDoc(elementWithDoc: PsiElement): String = {
    val e = elementWithDoc.getNavigationElement

    implicit def projectContext: ProjectContext = e.projectContext

    implicit def urlText: ScType => String = projectContext.typeSystem.urlText(_)

    val builder = new HtmlBuilderWrapper
    import builder._

    def appendDef(mainPart: => Unit): Unit =
      withTag("div", Seq(("class", "definition"))) {
        mainPart
      }

    def appendMainSection(element: PsiElement, epilogue: => Unit = {}, needsTpe: Boolean = false): Unit = {
      pre {
        element match {
          case an: ScAnnotationsHolder => append(parseAnnotations(an))
          case _ =>
        }

        val start = length

        element match {
          case m: ScModifierListOwner => append(parseModifiers(m))
          case _ =>
        }

        append(ScalaDocumentationUtils.getKeyword(element))

        b {
          append(element match {
            case named: ScNamedElement => escapeHtml(named.name)
            case _ => "_"
          })
        }

        element match {
          case tpeParamOwner: ScTypeParametersOwner =>
            append(parseTypeParameters(tpeParamOwner))
          case _ =>
        }

        element match {
          case par: ScParameterOwner =>
            append(parseParameters(par, length - start - 7).replaceAll("\n\\s*", ""))
          case _ =>
        }

        append(element match {
          case typed: ScTypedDefinition => typeAnnotation(typed)
          case _ if needsTpe            => ": Nothing"
          case _                        => ""
        })

        epilogue
      }
    }


    def appendTypeDef(typedef: ScTypeDefinition): HtmlBuilderWrapper = {
      appendDef {
        typedef.qualifiedName.lastIndexOf(".") match {
          case -1 =>
          case a =>
            withTag("font", Seq(("size", "-1"))) {
              b {
                append(typedef.qualifiedName.substring(0, a))
              }
            }
        }

        appendMainSection(typedef, {
          appendNl()
          append(parseExtendsBlock(typedef.extendsBlock))
        })
      }

      append(parseDocComment(typedef))
    }

    def appendFunction(fun: ScFunction): Unit = {
      appendDef {
        append(parseClassUrl(fun))
        appendMainSection(fun)
      }

      append(parseDocComment(fun))
    }

    def appendValOrVar(decl: ScValueOrVariable): Unit = {
      appendDef {
        decl match {
          case decl: ScMember => append(parseClassUrl(decl))
          case _ =>
        }
        appendMainSection(decl, needsTpe = true)
      }
      decl match {
        case doc: ScDocCommentOwner => append(parseDocComment(doc))
        case _ =>
      }
    }

    def appendTypeAlias(tpe: ScTypeAlias): Unit ={
      appendDef {
        append(parseClassUrl(tpe))
        appendMainSection(tpe, {
          tpe match {
            case definition: ScTypeAliasDefinition =>
              val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(psi.types.api.Any)
              append(s" = ${urlText(tp)}")
            case _ =>
          }
        })
      }

      append(parseDocComment(tpe))
    }

    def appendBindingPattern(pattern: ScBindingPattern): Unit = {
      pre {
        append("Pattern: ")
        b {
          append(escapeHtml(pattern.name))
        }
        append(typeAnnotation(pattern))
        if (pattern.getContext != null) {
          pattern.getContext.getContext match {
            case co: PsiDocCommentOwner => append(parseDocComment(co))
            case _ =>
          }
        }
      }
    }

    withHtmlMarkup {
      e match {
        case typeDef: ScTypeDefinition => appendTypeDef(typeDef)
        case fun: ScFunction           => appendFunction(fun)
        case decl: ScValueOrVariable   => appendValOrVar(decl)
        case param: ScParameter        => appendMainSection(param)
        case tpe: ScTypeAlias          => appendTypeAlias(tpe)
        case pattern: ScBindingPattern => appendBindingPattern(pattern)
        case _                         =>
      }
    }

    val result = builder.result()
    result
  }

  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.containingClass
    if (clazz == null) EmptyDoc
    else s"""<a href="psi_element://${escapeHtml(clazz.qualifiedName)}"><code>${escapeHtml(clazz.qualifiedName)}</code></a>"""
  }

  // TODO Either use this method only in the DocumentationProvider, or place it somewhere else
  // It supposed to be implementation details of the provider, but it's not (yet it does some strange things, adds \n).
  // When one needs to update the provider, it's hard to predict what any change might affect outside, and how.
  def parseParameters(elem: ScParameterOwner, spaces: Int)
                     (implicit typeToString: ScType => String): String = {
    elem.allClauses.map(parseParameterClause(_, spaces)).mkString("\n")
  }

  private def parseParameterClause(elem: ScParameterClause, spaces: Int)
                                  (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(" ")
    buffer.append(" " * spaces)
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    val strings = elem.parameters.map(parseParameter(_, memberModifiers = false))
    strings.mkString(if (elem.isImplicit) "(implicit " else "(", separator, ")")
  }

  // TODO "format", not "parse"?
  // TODO The method in DocumentationProvider should not be used from... everywhere.
  def parseParameter(param: ScParameter, escape: Boolean = true, memberModifiers: Boolean = true)
                    (implicit typeToString: ScType => String): String = {
    val member = param match {
      case c: ScClassParameter => c.isClassMember
      case _ => false
    }
    val buffer: StringBuilder = new StringBuilder
    // When parameter is val, var, or case class val, annotations are related to member, not to parameter
    if (!member || memberModifiers) {
      buffer.append(parseAnnotations(param, ' ', escape))
    }
    if (memberModifiers) {
      param match {
        case cl: ScClassParameter => buffer.append(parseModifiers(cl))
        case _ =>
      }
      buffer.append(param match {
        case c: ScClassParameter if c.isVal => "val "
        case c: ScClassParameter if c.isVar => "var "
        case _ => ""
      })
    }
    buffer.append(if (escape) escapeHtml(param.name) else param.name)

    buffer.append(typeAnnotation(param))

    buffer.toString()
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    // todo hyperlink identifiers in type bounds
    if (typeParameters.nonEmpty)
      escapeHtml(typeParameters.map(PresentationUtil.presentationString(_)).mkString("[", ", ", "]"))
    else EmptyDoc
  }

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder()
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        buffer.append(typeToString(seq.head.`type`().getOrAny) + "\n")
        for (i <- 1 until seq.length)
          buffer append " with " + typeToString(seq(i).`type`().getOrAny)
      case None =>
        if (elem.isUnderCaseClass) {
          buffer.append("<a href=\"psi_element://scala.Product\"><code>Product</code></a>")
        }
    }

    if (buffer.isEmpty) EmptyDoc
    else " extends " + buffer
  }

  private def parseModifiers(elem: ScModifierListOwner): String = {
    val buffer: StringBuilder = new StringBuilder

    def accessQualifier(x: ScAccessModifier): String = x.getReference match {
      case null => ""
      case ref => ref.resolve match {
        case clazz: PsiClass => "[<a href=\"psi_element://" +
          escapeHtml(clazz.qualifiedName) + "\"><code>" +
          (x.idText match {
            case Some(text) => text
            case None => ""
          }) + "</code></a>]"
        case pack: PsiPackage => "[" + escapeHtml(pack.getQualifiedName) + "]"
        case _ => x.idText match {
          case Some(text) => "[" + text + "]"
          case None => ""
        }
      }
    }

    for {
      modifier <- elem.getModifierList.accessModifier

      prefix = if (modifier.isPrivate) PsiModifier.PRIVATE
      else PsiModifier.PROTECTED

      suffix = if (modifier.isThis) "[this]"
      else accessQualifier(modifier)
    } buffer.append(prefix)
      .append(" ")
      .append(suffix)

    val modifiers = Array("abstract", "final", "sealed", "implicit", "lazy", "override")
    for (modifier <- modifiers if elem.hasModifierPropertyScala(modifier)) buffer.append(modifier + " ")
    buffer.toString()
  }

  private def parseAnnotations(elem: ScAnnotationsHolder,
                               sep: Char = '\n', escape: Boolean = true)
                              (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder

    def parseAnnotation(elem: ScAnnotation): String = {
      val res = new StringBuilder("@")
      val constrInvocation: ScConstructorInvocation = elem.constructorInvocation
      res.append(typeToString(constrInvocation.typeElement.`type`().getOrAny))

      val attrs = elem.annotationExpr.getAnnotationParameters
      if (attrs.nonEmpty) res append attrs.map(_.getText).mkString("(", ", ", ")")

      res.toString()
    }

    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + sep)
    }
    buffer.toString()
  }


  // TODO: strange naming.. not "parse", it not only parses but also resolves base
  private def parseDocComment(elem: PsiDocCommentOwner, isInherited: Boolean = false): String = {
    val docHtml = Option(elem.getDocComment) match {
      case Some(docComment) =>
        val commentParsed = docComment match {
          case scalaDoc: ScDocComment => parseScalaDocComment(elem, scalaDoc)
          case _                      => generateJavadocContent(elem)
        }
        if (isInherited) {
          wrapWithInheritedDescription(elem.containingClass)(commentParsed)
        } else {
          commentParsed
        }
      case None =>
        elem match {
          case method: PsiMethod =>
            parseDocCommentForBaseMethod(method).getOrElse(EmptyDoc)
          case _ =>
            EmptyDoc
        }
    }
    docHtml
  }

  // TODO: should we show inherited doc by default?
  //  what about @inheritdoc scaladoc tag then?
  private def parseDocCommentForBaseMethod(method: PsiMethod): Option[String] = {
    def selectActualMethod(base: PsiMethod): PsiMethod =
      base.getNavigationElement match {
        case m: PsiMethod => m
        case _            => base
      }

    val baseMethod = method match {
      case scalaMethod: ScFunction => scalaMethod.superMethod
      case javaMethod              => Option(SuperMethodsSearch.search(javaMethod, null, true, false).findFirst).map(_.getMethod)
    }
    baseMethod
      .map(selectActualMethod)
      .map(parseDocComment(_, isInherited = true))
  }

  private def parseScalaDocComment(
    elem: PsiDocCommentOwner,
    docComment: ScDocComment
  ): String = {
    val withReplacedWikiTags = ScaladocWikiProcessor.replaceWikiWithTags(docComment)
    val javaElement = createFakeJavaElement(elem, withReplacedWikiTags)
    generateJavadocContent(javaElement)
  }

  private def createFakeJavaElement(elem: PsiDocCommentOwner, docText: String) = {
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
           | class A${getTypeParams(typeAlias.typeParameters)} {}""".stripMargin
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
    elem match {
      case _: ScFunction | _: ScClass | _: PsiMethod => clazz.getAllMethods.head
      case _                                         => clazz
    }
  }

  private def createDummyJavaFile(text: String, project: Project): PsiJavaFile =
    PsiFileFactory.getInstance(project).createFileFromText("dummy", StdFileTypes.JAVA, text).asInstanceOf[PsiJavaFile]

  private def generateJavadoc(element: PsiElement): String = {
    val builder = new java.lang.StringBuilder()
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateDocInfoCore(builder, false)
    builder.toString
  }

  private def generateJavadocContent(element: PsiElement): String = {
    val javadoc = generateJavadoc(element)
    // TODO: this is far fro perfect to rely on text... =(
    //  dive deep into Javadoc generation and implement in a more safe and structural way
    val contentStartIdx = javadoc.indexOf("<div class='content'>") match {
      case -1 => javadoc.indexOf("<table class='sections'>")
      case idx => idx
    }
    val javadocFixed = if (contentStartIdx > 0) javadoc.substring(contentStartIdx) else javadoc
    javadocFixed
  }

  private def wrapWithInheritedDescription(clazz: PsiClass)(text: String): String = {
    val prefix =
      s"""<div class='content'>
         |<b>Description copied from class: </b>
         |<a href="psi_element://${escapeHtml(clazz.qualifiedName)}">
         |<code>${escapeHtml(clazz.name)}</code>
         |</a>
         |</div>""".stripMargin
    prefix + text
  }
}
