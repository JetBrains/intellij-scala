package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.{JavaDocInfoGenerator, JavaDocUtil}
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.psi.search.searches.SuperMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{PresentationUtil, ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Success, Try}

/**
  * User: Alexander Podkhalyuzin
  * Date: 11.11.2008
  */

class ScalaDocumentationProvider extends CodeDocumentationProvider {

  import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._

  def getDocumentationElementForLookupItem(psiManager: PsiManager, obj: Object,
                                           element: PsiElement): PsiElement = {
    obj match {
      case (_, element: PsiElement, _) => element
      case el: ScalaLookupItem => el.element
      case element: PsiElement => element
      case _ => null
    }
  }

  def getUrlFor(element: PsiElement, originalElement: PsiElement): java.util.List[String] = null

  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    val substitutor = originalElement match {
      case ref: ScReferenceElement =>
        ref.bind() match {
          case Some(ScalaResolveResult(_, subst)) => subst
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }

    ScalaDocumentationProvider.getQuickNavigateInfo(element, substitutor)
  }


  def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement = 
    JavaDocUtil.findReferenceTarget(psiManager, link, context)

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val containingFile = element.getContainingFile

    if (!containingFile.isInstanceOf[ScalaFile]) {
      if (element.isInstanceOf[ScalaPsiElement])
        debugMessage("Asked to build doc for a scala element, but it is in non scala file (1)", element)

      return null
    }

    val elementWithDoc = getElementWithDoc(element)
    if (elementWithDoc == null) {
      debugMessage("No actual doc owner found for element (2)", element)
      return null
    }

    val e = elementWithDoc.getNavigationElement

    implicit def projectContext: ProjectContext = e.projectContext

    implicit def urlText: ScType => String = projectContext.typeSystem.urlText(_)

    val builder = new HtmlBuilderWrapper
    import builder._

    def appendDef(mainPart: => Unit = {}): Unit = {
      withTag("div", Seq(("class", "definition"))) {
        mainPart
      }
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

        append(getKeyword(element))

        b {
          append(element match {
            case named: ScNamedElement => escapeHtml(named.name)
            case _ => "_"
          })
        }

        element match {
          case tpeParamOwner: ScTypeParametersOwner => append(parseTypeParameters(tpeParamOwner))
          case _ =>
        }

        element match {
          case par: ScParameterOwner => append(parseParameters(par, length - start - 7).replaceAll("\n\\s*", ""))
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


    withHtmlMarkup {
      e match {
        case clazz: ScTypeDefinition =>
          clazz.qualifiedName.lastIndexOf(".") match {
            case -1 =>
            case a =>
              appendDef {
                withTag("font", Seq(("size", "-1"))) {
                  b {
                    append(clazz.qualifiedName.substring(0, a))
                  }
                }

                appendMainSection(clazz, {
                  appendNl()
                  append(parseExtendsBlock(clazz.extendsBlock))
                })
              }
              
          }

          append(parseDocComment(clazz))
        case fun: ScFunction =>
          appendDef {
            append(parseClassUrl(fun))
            appendMainSection(fun)
          }
          
          append(parseDocComment(fun))
        case decl: ScDeclaredElementsHolder if decl.isInstanceOf[ScValue] || decl.isInstanceOf[ScVariable] =>
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
        case param: ScParameter =>
          appendMainSection(param)
        case tpe: ScTypeAlias =>
          appendDef {
            append(parseClassUrl(tpe))
            appendMainSection(tpe, {
              tpe match {
                case definition: ScTypeAliasDefinition =>
                  val tp = definition.aliasedTypeElement.flatMap {
                    _.`type`().toOption
                  }.getOrElse(Any)
                  append(s" = ${urlText(tp)}")
                case _ =>
              }
            })
          }
          
          append(parseDocComment(tpe))
        case pattern: ScBindingPattern =>
          pre {
            append("Pattern: ")
            b {
              append(escapeHtml(pattern.name))
            }
            append(typeAnnotation(pattern))
            if (pattern.getContext != null)
              pattern.getContext.getContext match {
                case co: PsiDocCommentOwner => append(parseDocComment(co))
                case _ =>
              }
          }
        case _ =>
      }
    }

    builder.result()
  }


  def findExistingDocComment(contextElement: PsiComment): PsiComment = {
    contextElement match {
      case comment: ScDocComment =>
        val commentOwner = comment.getOwner
        if (commentOwner != null) return commentOwner.getDocComment
      case _ =>
    }

    null
  }

  def generateDocumentationContentStub(contextComment: PsiComment): String = contextComment match {
    case scalaDocComment: ScDocComment => ScalaDocumentationProvider createScalaDocStub scalaDocComment.getOwner
    case _ => ""
  }

  def parseContext(startPoint: PsiElement): Pair[PsiElement, PsiComment] = {
    @tailrec
    def findDocCommentOwner(elem: PsiElement): Option[ScDocCommentOwner] = {
      elem match {
        case null => None
        case d: ScDocCommentOwner => Some(d)
        case _ => findDocCommentOwner(elem.getParent)
      }
    }

    findDocCommentOwner(startPoint).map(d => Pair.create(d.asInstanceOf[PsiElement],
      d.getDocComment.asInstanceOf[PsiComment])).orNull
  }
}

object ScalaDocumentationProvider {

  def getQuickNavigateInfo(element: PsiElement, substitutor: ScSubstitutor): String = {
    val text = element match {
      case clazz: ScTypeDefinition                         => generateClassInfo(clazz, substitutor)
      case function: ScFunction                            => generateFunctionInfo(function, substitutor)
      case value@inNameContext(_: ScValue | _: ScVariable) => generateValueInfo(value, substitutor)
      case alias: ScTypeAlias                              => generateTypeAliasInfo(alias, substitutor)
      case parameter: ScParameter                          => generateParameterInfo(parameter, substitutor)
      case b: ScBindingPattern                             => generateBindingPatternInfo(b, substitutor)
      case _                                               => null
    }

    if (text != null) text.replace("<", "&lt;") else null
  }

  def getQuickNavigateInfo(resolveResult: ScalaResolveResult): String =
    getQuickNavigateInfo(resolveResult.element, resolveResult.substitutor)

  private class HtmlBuilderWrapper(delegate: StringBuilder) {
    def this() {
      this(new StringBuilder(""))
    }

    def append(txt: String): HtmlBuilderWrapper = {
      delegate.append(txt)
      this
    }

    def append(any: Any): HtmlBuilderWrapper = {
      delegate.append(any)
      this
    }

    def appendNl(): Unit = append("\n")

    def length: Int = delegate.length

    def withTag(tag: String)(inner: => Unit) {
      append(s"<$tag>")
      inner
      append(s"</$tag>")
    }

    def withTag(tag: String, params: Seq[(String, String)])(inner: => Unit) {
      append(s"<$tag ")
      for ((name, value) <- params) append(name + "=\"" + value + "\"")
      append(">")
      inner
      append(s"</$tag>")
    }

    def withHtmlMarkup(inner: => Unit) {
      html {
        body {
          inner
        }
      }
    }

    def html(inner: => Unit): Unit = withTag("html")(inner)

    def body(inner: => Unit): Unit = withTag("body")(inner)

    def pre(inner: => Unit): Unit = withTag("pre")(inner)

    def b(inner: => Unit): Unit = withTag("b")(inner)

    def u(inner: => Unit): Unit = withTag("u")(inner)

    def i(inner: => Unit): Unit = withTag("i")(inner)

    def tt(inner: => Unit): Unit = withTag("tt")(inner)

    def sub(inner: => Unit): Unit = withTag("sub")(inner)

    def sup(inner: => Unit): Unit = withTag("sup")(inner)

    def result(): String = delegate.result()
  }

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider")

  private def debugMessage(msg: String, elem: PsiElement) {
    val footer = if (!elem.isValid) {
      s"[Invalid Element: ${elem.getNode} ${elem.getClass.getName}]"
    } else if (elem.getContainingFile == null) {
      s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: NULL]"
    } else s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: ${elem.getContainingFile.getName}] [Language: ${elem.getContainingFile.getLanguage}]"

    LOG debug s"[ScalaDocProvider] [ $msg ] $footer"
  }

  val replaceWikiScheme = Map("__" -> "u>", "'''" -> "b>", "''" -> "i>", "`" -> "tt>", ",," -> "sub>", "^" -> "sup>")

  private trait MacroFinder {
    def getMacroBody(name: String): Option[String]
  }

  private class MacroFinderDummy extends MacroFinder {
    override def getMacroBody(name: String): Option[String] = None
  }

  private class MacroFinderImpl(comment: ScDocComment, handler: PsiElement => String = { element => element.getText }) extends MacroFinder {
    private val myCache = mutable.HashMap[String, String]()
    private var lastProcessedComment: Option[PsiDocComment] = None

    private val processingQueue = mutable.Queue.apply[ScDocCommentOwner]()
    private var init = false

    def getMacroBody(name: String): Option[String] = {
      if (!init) fillQueue()
      if (myCache contains name) return myCache get name

      var commentToProcess = selectComment2()

      while (commentToProcess.isDefined) {
        commentToProcess foreach {
          c =>
            c.getTags.filter(_.getName == MyScaladocParsing.DEFINE_TAG) map {
              case tag: ScDocTag =>
                val vEl = tag.getValueElement
                val a = (if (vEl != null) vEl.getText else "", tag.getAllText(handler).trim)

                if (a._1 != "") myCache += a
                a
            } foreach {
              case (tName, v) if tName == name => return Option(v)
              case _ =>
            }
        }

        lastProcessedComment = commentToProcess
        commentToProcess = selectComment2()
      }

      None
    }

    private def fillQueue() {
      def fillInner(from: Iterable[ScDocCommentOwner]) {
        if (from.isEmpty) return
        val tc = mutable.ArrayBuffer.apply[ScDocCommentOwner]()

        from foreach {
          case clazz: ScTemplateDefinition =>
            processingQueue enqueue clazz

            clazz.supers foreach {
              case cz: ScDocCommentOwner => tc += cz
              case _ =>
            }
          case member: ScMember if member.hasModifierProperty("override") =>
            processingQueue enqueue member

            member match {
              case named: ScNamedElement =>
                ScalaPsiUtil.superValsSignatures(named) map (sig => sig.namedElement) foreach {
                  case od: ScDocCommentOwner => tc += od
                  case _ =>
                }
              case _ =>
            }

            member.containingClass match {
              case od: ScDocCommentOwner => tc += od
              case _ =>
            }
          case member: ScMember if member.getContainingClass != null =>
            processingQueue enqueue member

            member.containingClass match {
              case od: ScDocCommentOwner => tc += od
              case _ =>
            }
          case _ => return
        }

        fillInner(tc)
      }

      init = true
      comment.getOwner match {
        case od: ScDocCommentOwner => fillInner(Option(od))
        case _ =>
      }
    }

    private def selectComment2(): Option[ScDocComment] = {
      while (processingQueue.nonEmpty) {
        val next = processingQueue.dequeue()

        if (next.docComment.isDefined) return next.docComment
      }

      None
    }
  }

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
    if (clazz == null) return ""
    "<a href=\"psi_element://" + escapeHtml(clazz.qualifiedName) + "\"><code>" +
      escapeHtml(clazz.qualifiedName) + "</code></a>"
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
    for (_ <- 1 to spaces) buffer.append(" ")
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    elem.parameters.map(parseParameter(_, memberModifiers = false)).
      mkString(if (elem.isImplicit) "(implicit " else "(", separator, ")")
  }

  def createScalaDocStub(commentOwner: PsiDocCommentOwner): String = {
    if (!commentOwner.getContainingFile.isInstanceOf[ScalaFile]) return ""

    val buffer = new StringBuilder("")
    val leadingAsterisks = "* "

    val inheritedParams = mutable.HashMap.apply[String, PsiDocTag]()
    val inheritedTParams = mutable.HashMap.apply[String, PsiDocTag]()

    import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._

    def registerInheritedParam(allParams: mutable.HashMap[String, PsiDocTag], param: PsiDocTag) {
      if (!allParams.contains(param.getValueElement.getText)) {
        allParams.put(param.getValueElement.getText, param)
      }
    }

    def processProbablyJavaDocCommentWithOwner(owner: PsiDocCommentOwner) {
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

    def processParams(owner: ScParameterOwner) {
      for (param <- owner.parameters) {
        if (inheritedParams contains param.name) {
          val paramText = inheritedParams(param.name).getText
          buffer append leadingAsterisks append paramText.substring(0, paramText.lastIndexOf("\n") + 1)
        } else {
          buffer append leadingAsterisks append PARAM_TAG append " " append param.name append "\n"
        }
      }
    }

    def processTypeParams(owner: ScTypeParametersOwner) {
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
          annotation.constructor.args.foreach(a =>
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
      case c: ScClassParameter => c.isEffectiveVal
      case _ => false
    }
    val buffer: StringBuilder = new StringBuilder("")
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
    else ""
  }

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(" extends ")
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        buffer.append(typeToString(seq.head.`type`().getOrAny) + "\n")
        for (i <- 1 until seq.length)
          buffer append " with " + typeToString(seq(i).`type`().getOrAny)
      case None =>
        buffer.append("<a href=\"psi_element://scala.ScalaObject\"><code>ScalaObject</code></a>")
        if (elem.isUnderCaseClass) {
          buffer.append("<a href=\"psi_element://scala.Product\"><code>Product</code></a>")
        }
    }

    buffer.toString()
  }

  private def parseModifiers(elem: ScModifierListOwner): String = {
    val buffer: StringBuilder = new StringBuilder("")

    def accessQualifier(x: ScAccessModifier): String = (x.getReference match {
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
    }) + " "

    buffer.append(elem.getModifierList.accessModifier match {
      case Some(x: ScAccessModifier) => x.access match {
        case ScAccessModifier.Type.PRIVATE => "private" + accessQualifier(x)
        case ScAccessModifier.Type.PROTECTED => "protected" + accessQualifier(x)
        case ScAccessModifier.Type.THIS_PRIVATE => "private[this] "
        case ScAccessModifier.Type.THIS_PROTECTED => "protected[this] "
      }
      case None => ""
    })
    val modifiers = Array("abstract", "final", "sealed", "implicit", "lazy", "override")
    for (modifier <- modifiers if elem.hasModifierPropertyScala(modifier)) buffer.append(modifier + " ")
    buffer.toString()
  }

  private def parseAnnotations(elem: ScAnnotationsHolder,
                               sep: Char = '\n', escape: Boolean = true)
                              (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("")

    def parseAnnotation(elem: ScAnnotation): String = {
      val res = new StringBuilder("@")
      val constr: ScConstructor = elem.constructor
      res.append(typeToString(constr.typeElement.`type`().getOrAny))

      val attrs = elem.annotationExpr.getAnnotationParameters
      if (attrs.nonEmpty) res append attrs.map(_.getText).mkString("(", ", ", ")")

      res.toString()
    }

    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + sep)
    }
    buffer.toString()
  }


  private def parseDocComment(elem: PsiDocCommentOwner, withDescription: Boolean = false): String = {
    def getParams(fun: ScParameterOwner): String =
      fun.parameters.map(param => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")

    def getTypeParams(tParams: Seq[ScTypeParam]): String =
      if (tParams.nonEmpty)
        tParams.map(param => escapeHtml(param.name)).mkString("<", " , ", ">")
      else ""

    elem.getDocComment match {
      case scDocComment: ScDocComment =>
        val replacedText = replaceWikiWithTags(scDocComment)
        val xText = if (replacedText == null) "" else replacedText.getText

        val text = elem match {
          case clazz: ScClass =>
            "\nclass A {\n " + xText + " \npublic " + getTypeParams(clazz.typeParameters) + "void f" +
              getParams(clazz) + " {\n}\n}"
          case typeAlias: ScTypeAlias => xText + "\n class A" + getTypeParams(typeAlias.typeParameters) + " {}"
          case _: ScTypeDefinition => xText + "\nclass A {\n }"
          case f: ScFunction =>
            "class A {\n" + xText + "\npublic " + getTypeParams(f.typeParameters) + "int f" + getParams(f) + " {}\n}"
          case m: PsiMethod =>
            "class A {\n" + m.getText + "\n}"
          case _ => xText + "\nclass A"
        }
        val dummyFile = PsiFileFactory.getInstance(elem.getProject).createFileFromText("dummy", StdFileTypes.JAVA, text).asInstanceOf[PsiJavaFile]

        val lightElement = elem match {
          case _: ScFunction | _: ScClass | _: PsiMethod =>
            dummyFile.getClasses.head.getAllMethods.head
          case _ => dummyFile.getClasses.head
        }

        val javadoc = new java.lang.StringBuilder()
        new JavaDocInfoGenerator(elem.getProject, lightElement).generateDocInfoCore(javadoc, false)

        val (s1, s2) = elem.containingClass match {
          case e: PsiClass if withDescription => ("<b>Description copied from class: </b><a href=\"psi_element://" +
            escapeHtml(e.qualifiedName) + "\"><code>" + escapeHtml(e.name) + "</code></a><p>", "</p>")
          case _ => ("", "")
        }
        s1 + javadoc.toString.substring(javadoc.indexOf("<div class='content'>")) + s2
      case _ =>
        def selectActualMethod(base: PsiMethod): PsiMethod = base.getNavigationElement match {
          case m: PsiMethod => m
          case _ => base
        }

        val baseMethod = elem match {
          case fun: ScFunction => fun.superMethod match {
            case Some(sfun) => sfun
            case _ => return ""
          }
          case method: PsiMethod =>
            Try(SuperMethodsSearch.search(method, null, true, false).findFirst) match {
              case Success(ss) => ss.getMethod
              case _ => return ""
            }
          case _ => return ""
        }

        parseDocComment(selectActualMethod(baseMethod), withDescription = true)
    }
  }

  private def getWikiTextRepresentation(macroFinder: MacroFinder)(comment: PsiElement): (mutable.StringBuilder, mutable.StringBuilder) = {
    val commentBody = new StringBuilder("")
    val tagsPart = new StringBuilder("")
    var isFirst = true

    def visitTags(element: ScDocTag) {
      element.name match {
        case MyScaladocParsing.TODO_TAG | MyScaladocParsing.NOTE_TAG | MyScaladocParsing.EXAMPLE_TAG | MyScaladocParsing.SEE_TAG =>
          if (isFirst) {
            commentBody.append("<br/><br/>")
            isFirst = false
          }
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi))
          commentBody.append("<br/><br/>")
        case MyScaladocParsing.INHERITDOC_TAG =>
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi, commentBody))
        case _ =>
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi, tagsPart))
      }
    }

    def visitElementInner(element: PsiElement, result: StringBuilder = commentBody) {
      if (element.getFirstChild == null) {
        element.getNode.getElementType match {
          case ScalaDocTokenType.DOC_TAG_NAME =>
            element.getText match {
              case MyScaladocParsing.TYPE_PARAM_TAG => result.append("@param ")
              case MyScaladocParsing.NOTE_TAG | MyScaladocParsing.TODO_TAG | MyScaladocParsing.EXAMPLE_TAG =>
                result.append("<b>").append(element.getText.substring(1).capitalize).append(":</b><br/>")
              case MyScaladocParsing.SEE_TAG =>
                result.append("<b>").append("See also").append(":</b><br/>")
              case MyScaladocParsing.INHERITDOC_TAG =>
                val inherited = element.getParent.getParent.getParent match {
                  case fun: ScFunction => (fun.superMethod map (_.getDocComment)).orNull
                  case clazz: ScTemplateDefinition => (clazz.supers.headOption map (_.getDocComment)).orNull
                  case _ => null
                }

                if (inherited != null) {
                  val (inheritedBody, _) = getWikiTextRepresentation(macroFinder)(inherited)
                  result append inheritedBody.toString().stripPrefix("/**").stripSuffix("*/")
                }
              case _ => result.append(element.getText)
            }
          case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN
            if element.getParent.getParent.getFirstChild.getText == MyScaladocParsing.TYPE_PARAM_TAG =>
            result.append("<" + element.getText + ">")
          case ScalaDocTokenType.DOC_INNER_CODE_TAG => result.append(" <pre> {@code ")
          case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG => result.append(" } </pre> ")
          case ScalaDocTokenType.VALID_DOC_HEADER =>
            val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
            result.append("<h" + headerSize + ">")
          case ScalaDocTokenType.DOC_HEADER =>
            if (element.getParent.getFirstChild.getNode.getElementType == ScalaDocTokenType.VALID_DOC_HEADER) {
              val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
              result.append("</h" + headerSize + ">")
            } else {
              result.append(element.getText)
            }
          case ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
            result.append("<a href=\"")
          case ScalaDocTokenType.DOC_LINK_TAG => result.append("{@link ")
          case ScalaDocTokenType.DOC_LINK_CLOSE_TAG =>
            if (element.getParent.getNode.getFirstChildNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG) {
              val linkText = element.getPrevSibling.getText
              if (linkText.trim().contains(" ")) {
                val trimmedText = linkText.trim()
                val spaceIndex = trimmedText.indexOf(" ")
                result.append(trimmedText.substring(0, spaceIndex)).append("\">").append(trimmedText.substring(spaceIndex + 1)).append("</a>")
              } else {
                result.append("\">" + linkText + "</a>")
              }
            } else {
              result.append("}")
            }
          case ScalaDocTokenType.DOC_COMMENT_DATA if element.getParent.isInstanceOf[ScDocTag] &&
            element.getParent.asInstanceOf[ScDocTag].name == MyScaladocParsing.SEE_TAG =>
            result.append("<dd>").append(element.getText.trim()).append("</dd>")
          case ScalaDocTokenType.DOC_COMMENT_DATA
            if element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
            if (!element.getText.trim().contains(" ")) {
              result.append(element.getText)
            }
          case _ if replaceWikiScheme.contains(element.getText) &&
            (element.getParent.getFirstChild == element || element.getParent.getLastChild == element) =>
            val prefix = if (element.getParent.getFirstChild == element) "<" else "</"
            result.append(prefix + replaceWikiScheme(element.getText))
          case _ if element.getParent.getLastChild == element && // do not swap this & last cases
            replaceWikiScheme.contains(element.getParent.getFirstChild.getText) =>
            result.append(element.getText).append("</")
            result.append(replaceWikiScheme(element.getParent.getFirstChild.getText))
          case ScalaDocTokenType.DOC_COMMENT_END => tagsPart.append(element.getText)
          case ScalaDocTokenType.DOC_MACROS => try {
            macroFinder.getMacroBody(element.getText.stripPrefix("$")).map(a => result append a).getOrElse(result append s"[Cannot find macro: ${element.getText}]")
          } catch {
            case _: Exception =>
          }
          case _ => result.append(element.getText)
        }
      } else {
        for (child <- element.getNode.getChildren(null)) {
          child.getPsi match {
            case tag: ScDocTag => visitTags(tag)
            case _ => visitElementInner(child.getPsi, result)
          }
        }
      }
    }


    visitElementInner(comment)
    (commentBody, tagsPart)
  }

  private def replaceWikiWithTags(comment: PsiDocComment): PsiDocComment = {
    if (!comment.isInstanceOf[ScDocComment]) return comment
    val macroFinder = new MacroFinderImpl(comment.asInstanceOf[ScDocComment], { element =>
      val a = getWikiTextRepresentation(new MacroFinderDummy)(element)
      a._1.result()
    })

    val (commentBody, tagsPart) = getWikiTextRepresentation(macroFinder)(comment)
    val text = commentBody.append("<br/>\n").append(tagsPart).append(" class a {}").toString()
    val scalaComment = createScalaFileFromText(text)(comment.getManager).typeDefinitions.head.getDocComment

    scalaComment
  }

  @tailrec
  private def getElementWithDoc(originalElement: PsiElement): PsiElement = {
    originalElement match {
      case null => null
      case ScFunctionWrapper(delegate) => delegate
      case _: ScTypeDefinition | _: ScTypeAlias | _: ScValue
           | _: ScVariable | _: ScFunction | _: ScParameter | _: ScBindingPattern => originalElement
      case _ => getElementWithDoc(originalElement.getParent)
    }
  }

  private def getMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    member.containingClass.name + " " + member.containingClass.getPresentation.getLocationString + "\n"
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val i = trimed.indexOf('\n')
    if (i == -1) trimed else trimed.substring(0, i) + " ..."
  }

  private def appendTypeParams(owner: ScTypeParametersOwner, buffer: StringBuilder) {
    buffer.append(owner.typeParametersClause match {
      case Some(x) => x.getText
      case None => ""
    })
  }

  def generateClassInfo(clazz: ScTypeDefinition, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    val module = ModuleUtilCore.findModuleForPsiElement(clazz)
    if (module != null) {
      buffer.append('[').append(module.getName).append("] ")
    }
    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1) buffer.append(locationString.substring(1, length - 1))
    if (buffer.nonEmpty) buffer.append("\n")
    buffer.append(getModifiersPresentableText(clazz.getModifierList))
    buffer.append(getKeyword(clazz))
    buffer.append(clazz.name)
    appendTypeParams(clazz, buffer)
    clazz match {
      case clazz: ScClass =>
        clazz.constructor match {
          case Some(x: ScPrimaryConstructor) =>
            buffer.append(StructureViewUtil.getParametersAsString(x.parameterList, short = false, subst))
          case None =>
        }
      case _ =>
    }
    buffer.append(" extends")
    val types = clazz.superTypes
    if (types.nonEmpty) {
      for (i <- types.indices) {
        buffer.append(if (i == 1) "\n  " else " ")
        if (i != 0) buffer.append("with ")
        buffer.append(subst.subst(types(i)).presentableText)
      }
    }
    buffer.toString()
  }

  def generateFunctionInfo(function: ScFunction, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    val list = function.getModifierList
    if (list != null) {
      buffer.append(getModifiersPresentableText(list))
    }
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function, subst))
    buffer.toString()
  }

  def generateValueInfo(field: PsiNamedElement, subst: ScSubstitutor): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.append(getModifiersPresentableText(member.getModifierList))
    member match {
      case value: ScValue =>
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst.subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText)
          case _ =>
        }
        value match {
          case d: ScPatternDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
      case variable: ScVariable =>
        buffer.append("var ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst.subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText)
          case _ =>
        }
        variable match {
          case d: ScVariableDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
    }
    buffer.toString()
  }

  def generateBindingPatternInfo(binding: ScBindingPattern, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    val typez = subst.subst(subst.subst(binding.`type`().getOrAny))
    if (typez != null) buffer.append(": " + typez.presentableText)

    buffer.toString()
  }

  def generateTypeAliasInfo(alias: ScTypeAlias, subst: ScSubstitutor): String = {

    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    appendTypeParams(alias, buffer)
    alias match {
      case d: ScTypeAliasDefinition =>
        buffer.append(" = ")
        val ttype = subst.subst(d.aliasedType.getOrAny)
        buffer.append(ttype.presentableText)
      case _ =>
    }
    buffer.toString()
  }

  def generateParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String = {
    contextBoundParameterInfo(parameter).getOrElse {
      simpleParameterInfo(parameter, subst)
    }
  }

  private def simpleParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String = {
    val name = parameter.name
    val typeAnnot = typeAnnotation(parameter)(subst.subst(_).presentableText)

    val defaultText = s"$name$typeAnnot"

    val prefix = parameter match {
      case clParameter: ScClassParameter =>
        clParameter.containingClass.toOption.map { clazz =>
          val classWithLocation = clazz.name + " " + clazz.getPresentation.getLocationString + "\n"
          val keyword = if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else ""

          classWithLocation + keyword

        }.getOrElse("")
      case _ => ""
    }
    prefix + defaultText
  }

  private def contextBoundParameterInfo(parameter: ScParameter): Option[String] = {
    ScalaPsiUtil.originalContextBound(parameter).map {

      case (typeParam, boundTypeElem) =>
        val tpName = typeParam.name
        val boundText = boundTypeElem.getText

        val clause = typeParam.typeParametersClause.map(_.getText).getOrElse("")
        s"context bound $tpName$clause : $boundText"
    }
  }

  private def getModifiersPresentableText(modifiers: ScModifierList): String = {
    val explicitModifiers =
      Option(modifiers).toSeq
        .flatMap(_.modifiers)
        .filterNot(_ == "public")
    explicitModifiers.map(_ + " ").mkString
  }

  private def getKeyword(element: PsiElement) = element match {
    case _: ScClass => "class "
    case _: ScObject => "object "
    case _: ScTrait => "trait "
    case _: ScFunction => "def "
    case c: ScClassParameter if c.isVal => "val "
    case c: ScClassParameter if c.isVar => "var "
    case _: ScValue => "val "
    case _: ScVariable => "var "
    case _ => ""
  }
}
