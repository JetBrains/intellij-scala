package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.{JavaDocInfoGenerator, JavaDocUtil}
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.psi.search.searches.SuperMethodsSearch
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{PresentationUtil, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable

class ScalaDocumentationProvider extends CodeDocumentationProvider {

  import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._

  override def getDocumentationElementForLookupItem(
    psiManager: PsiManager,
    obj: Object,
    element: PsiElement
  ): PsiElement =
    obj match {
      case (_, element: PsiElement, _) => element
      case el: ScalaLookupItem         => el.element
      case element: PsiElement         => element
      case _                           => null
    }

  override def getUrlFor(element: PsiElement, originalElement: PsiElement): java.util.List[String] = null

  override def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    val substitutor = originalElement match {
      case ref: ScReference =>
        ref.bind() match {
          case Some(ScalaResolveResult(_, subst)) => subst
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }

    ScalaDocumentationProvider.getQuickNavigateInfo(element, substitutor)
  }


  override def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement =
    JavaDocUtil.findReferenceTarget(psiManager, link, context)

  override def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val containingFile = element.getContainingFile

    if (!containingFile.isInstanceOf[ScalaFile]) {
      if (element.isInstanceOf[ScalaPsiElement])
        debugMessage(ScalaEditorBundle.message("doc.is.not.in.scala.file"), element)

      return null
    }

    val elementWithDoc = getElementWithDoc(element)
    if (elementWithDoc == null) {
      debugMessage(ScalaEditorBundle.message("no.doc.owner.for.element"), element)
      return null
    }

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
              val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(Any)
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


  override def findExistingDocComment(contextElement: PsiComment): PsiComment = {
    contextElement match {
      case comment: ScDocComment =>
        val commentOwner = comment.getOwner
        if (commentOwner != null) return commentOwner.getDocComment
      case _ =>
    }

    null
  }

  override def generateDocumentationContentStub(contextComment: PsiComment): String = contextComment match {
    case scalaDocComment: ScDocComment =>
      ScalaDocumentationProvider.createScalaDocStub(scalaDocComment.getOwner)
    case _ =>
      EmptyDoc
  }

  override def parseContext(startPoint: PsiElement): Pair[PsiElement, PsiComment] = {
    def findDocCommentOwner(elem: PsiElement): Option[ScDocCommentOwner] =
      elem.withParents.instanceOf[ScDocCommentOwner]

    val docOwner = Option(startPoint).flatMap(findDocCommentOwner)
    docOwner.map(d => Pair.create(d, d.getDocComment).asInstanceOf[Pair[PsiElement, PsiComment]]).orNull
  }
}

object ScalaDocumentationProvider {

  // TODO: review usages, maybe propper way will be to use null / None?
  private val EmptyDoc = ""

  def getQuickNavigateInfo(element: PsiElement, substitutor: ScSubstitutor): String =
    ScalaDocumentationQuickInfoGenerator.getQuickNavigateInfo(element, substitutor)

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider")

  private def debugMessage(msg: String, elem: PsiElement): Unit = {
    val footer = if (!elem.isValid) {
      s"[Invalid Element: ${elem.getNode} ${elem.getClass.getName}]"
    } else if (elem.getContainingFile == null) {
      s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: NULL]"
    } else {
      s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: ${elem.getContainingFile.getName}] [Language: ${elem.getContainingFile.getLanguage}]"
    }

    LOG debug s"[ScalaDocProvider] [ $msg ] $footer"
  }

  val replaceWikiScheme = Map(
    "__" -> "u>",
    "'''" -> "b>",
    "''" -> "i>",
    "`" -> "tt>",
    ",," -> "sub>",
    "^" -> "sup>"
  )

  private[documentationProvider]
  def typeAnnotation(elem: ScTypedDefinition)
                    (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(": ")
    val typez = elem match {
      case fun: ScFunction => fun.returnType.getOrAny
      case _ => elem.`type`().getOrAny
    }
    val typeText = elem match {
      case param: ScParameter => decoratedParameterType(param, typeToString(typez))
      case _                  => typeToString(typez)
    }
    buffer.append(typeText)
    buffer.toString()
  }

  private def decoratedParameterType(param: ScParameter, typeText: String): String = {
    val buffer = StringBuilder.newBuilder

    if (param.isCallByNameParameter) {
      val arrow = ScalaPsiUtil.functionArrow(param.getProject)
      buffer.append(s"$arrow ")
    }

    buffer.append(typeText)

    if (param.isRepeatedParameter) buffer.append("*")

    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpressionInSource match {
        case Some(expr) =>
          val text: String = expr.getText.replace(" /* compiled code */ ", "")
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo) buffer.append("...")
        case None => buffer.append("...")
      }
    }
    buffer.toString()
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

  def createScalaDocStub(commentOwner: PsiDocCommentOwner): String = {
    if (!commentOwner.getContainingFile.isInstanceOf[ScalaFile]) return ""

    val buffer = new StringBuilder
    val leadingAsterisks = "* "

    val inheritedParams = mutable.HashMap.apply[String, PsiDocTag]()
    val inheritedTParams = mutable.HashMap.apply[String, PsiDocTag]()

    import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._

    def registerInheritedParam(allParams: mutable.HashMap[String, PsiDocTag], param: PsiDocTag): Unit = {
      if (!allParams.contains(param.getValueElement.getText)) {
        allParams.put(param.getValueElement.getText, param)
      }
    }

    def processProbablyJavaDocCommentWithOwner(owner: PsiDocCommentOwner): Unit = {
      owner.getDocComment match {
        case scalaComment: ScDocComment =>
          for (docTag <- scalaComment.findTagsByName(Set(PARAM_TAG, TYPE_PARAM_TAG).contains _)) {
            docTag.name match {
              case PARAM_TAG => registerInheritedParam(inheritedParams, docTag)
              case TYPE_PARAM_TAG => registerInheritedParam(inheritedTParams, docTag)
            }
          }
        case javaComment: PsiDocComment =>
          for (paramTag <- javaComment findTagsByName "param") {
            if (paramTag.getValueElement.getText startsWith "<") {
              registerInheritedParam(inheritedTParams, paramTag)
            } else {
              registerInheritedParam(inheritedParams, paramTag)
            }
          }
        case _ =>
      }
    }

    def processParams(owner: ScParameterOwner): Unit = {
      for (param <- owner.parameters) {
        if (inheritedParams contains param.name) {
          val paramText = inheritedParams(param.name).getText
          buffer append leadingAsterisks append paramText.substring(0, paramText.lastIndexOf("\n") + 1)
        } else {
          buffer append leadingAsterisks append PARAM_TAG append " " append param.name append "\n"
        }
      }
    }

    def processTypeParams(owner: ScTypeParametersOwner): Unit = {
      for (tparam <- owner.typeParameters) {
        if (inheritedTParams.contains(tparam.name)) {
          val paramText = inheritedTParams(tparam.name).getText
          buffer.append(leadingAsterisks).append(paramText.substring(0, paramText.lastIndexOf("\n") + 1))
        } else if (inheritedTParams.contains("<" + tparam + ">")) {
          val paramTag = inheritedTParams("<" + tparam.name + ">")
          val descriptionText =
            paramTag.getText.substring(paramTag.getValueElement.getTextOffset + paramTag.getValueElement.getTextLength)
          val parameterName = paramTag.getValueElement.getText

          buffer.append(leadingAsterisks).append("@").append(paramTag.name).append(" ").
            append(parameterName.substring(1, parameterName.length - 1)).append(" ").
            append(descriptionText.substring(0, descriptionText.lastIndexOf("\n") + 1))
        } else {
          buffer.append(leadingAsterisks).append(TYPE_PARAM_TAG).append(" ").append(tparam.name).append("\n")
        }
      }
    }

    commentOwner match {
      case clazz: ScClass =>
        clazz.getSupers.foreach(processProbablyJavaDocCommentWithOwner)
        processParams(clazz)
        processTypeParams(clazz)
      case function: ScFunction =>
        val parents = function.findSuperMethods()
        var returnTag: String = null
        val needReturnTag = function.getReturnType != null && !function.hasUnitResultType

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)

          if (needReturnTag) {
            var inherRetTag: PsiDocTag = null
            parent.getDocComment match {
              case scComment: ScDocComment =>
                inherRetTag = scComment.findTagByName("@return")
              case comment: PsiDocComment =>
                inherRetTag = comment.findTagByName("return")
              case _ =>
            }
            if (inherRetTag != null) {
              returnTag = inherRetTag.getText.substring(0, inherRetTag.getText.lastIndexOf("\n") + 1)
            }
          }
        }

        processParams(function)
        processTypeParams(function)

        for (annotation <- function.annotations if annotation.annotationExpr.getText.startsWith("throws")) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.THROWS_TAG).append(" ")
          annotation.constructorInvocation.args.foreach(a =>
            a.exprs.headOption.map {
              exprHead =>
                exprHead.`type`() match {
                  case Right(head) =>
                    head match {
                      case ParameterizedType(_, args) =>
                        args.headOption match {
                          case a: Some[ScType] =>
                            a.get.extractClass match {
                              case Some(clazz) => buffer append clazz.qualifiedName
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
            }
          )

          buffer.append(" \n")
        }

        if (returnTag != null) {
          buffer.append(leadingAsterisks).append(returnTag)
        } else if (needReturnTag) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.RETURN_TAG).append(" \n")
        }
      case scType: ScTypeAlias =>
        val parents = ScalaPsiUtil.superTypeMembers(scType)
        for (parent <- parents if parent.isInstanceOf[ScTypeAlias]) {
          processProbablyJavaDocCommentWithOwner(parent.asInstanceOf[ScTypeAlias])
        }
        processTypeParams(scType)
      case traitt: ScTrait =>
        val parents = traitt.getSupers

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)
        }
        processTypeParams(traitt)
      case _ =>
    }

    buffer.toString()
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
    // TODO: do we need to create a new tag inside replaceWikiWithTags just to .getText on it?
    val withReplacedText = ScaladocWikiProcessor.replaceWikiWithTags(docComment)
    val docTextNormalized =
      if (withReplacedText == null) EmptyDoc // TODO: maybe shouldn't proceed if result is null
      else withReplacedText.getText
    val javaElement = createFakeJavaElement(elem, docTextNormalized)
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
    val javadocFixed = javadoc.substring(javadoc.indexOf("<div class='content'>"))
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

  @tailrec
  private def getElementWithDoc(originalElement: PsiElement): PsiElement =
    originalElement match {
      case null                        => null
      case ScFunctionWrapper(delegate) => delegate
      case _: ScTypeDefinition |
           _: ScTypeAlias |
           _: ScValue |
           _: ScVariable |
           _: ScBindingPattern |
           _: ScFunction |
           _: ScParameter              => originalElement
      case _                           => getElementWithDoc(originalElement.getParent)
    }
}
