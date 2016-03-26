package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.psi.search.searches.SuperMethodsSearch
import com.intellij.psi.util.{MethodSignatureBackedByPsiMethod, PsiTreeUtil}
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScConstructor, ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{PresentationUtil, ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends CodeDocumentationProvider {
  import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, obj : Object,
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
    
    val text = element match {
      case clazz: ScTypeDefinition => generateClassInfo(clazz, substitutor)
      case function: ScFunction => generateFunctionInfo(function, substitutor)
      case value: ScNamedElement if ScalaPsiUtil.nameContext(value).isInstanceOf[ScValue]
              || ScalaPsiUtil.nameContext(value).isInstanceOf[ScVariable] => generateValueInfo(value, substitutor)
      case alias: ScTypeAlias => generateTypeAliasInfo(alias, substitutor)
      case parameter: ScParameter => generateParameterInfo(parameter, substitutor)
      case b: ScBindingPattern => generateBindingPatternInfo(b, substitutor)
      case _ => null
    }
    
    if (text != null) text.replace("<", "&lt;") else null
  }

  def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement = {
    JavaDocUtil.findReferenceTarget(psiManager, link, context)
  }

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val containingFile = element.getContainingFile

    if (!containingFile.isInstanceOf[ScalaFile]) {
      if (element.isInstanceOf[ScalaPsiElement])
        debugMessage("Asked to build doc for a scala element, but it is in non scala file (1)", element)

      return null
    }

    val docedElement = getDocedElement(element)
    if (docedElement == null) {
      debugMessage("No actual doc owner found for element (2)", element)
      return null
    }

    val e = docedElement.getNavigationElement

    implicit def urlText: ScType => String = e.typeSystem.urlText(_)
    e match {
      case clazz: ScTypeDefinition =>
        val buffer: StringBuilder = new StringBuilder("")
        val qualName = clazz.qualifiedName
        val pack = {
          val lastIndexOf = qualName.lastIndexOf(".")
          if (lastIndexOf >= 0) qualName.substring(0, lastIndexOf) else ""
        }

        if (pack != "") buffer.append("<font size=\"-1\"><b>" + escapeHtml(pack) + "</b></font>")

        buffer.append("<PRE>")
        buffer.append(parseAnnotations(clazz))
        val start = buffer.length
        buffer.append(parseModifiers(clazz))
        buffer.append(clazz match {
          case _: ScClass => "class "
          case _: ScObject => "object "
          case _: ScTrait => "trait "
        })
        buffer.append("<b>" + escapeHtml(clazz.name) + "</b>")
        buffer.append(parseTypeParameters(clazz))
        val end = buffer.length
        clazz match {
          case par: ScParameterOwner => buffer.append(parseParameters(par, end - start - 7))
          case _ =>
        }
        buffer.append("\n")
        buffer.append(parseExtendsBlock(clazz.extendsBlock))
        buffer.append("</PRE>")
        buffer.append(parseDocComment(clazz))

        "<html><body>" + buffer.toString + "</body></html>"
      case fun: ScFunction =>
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append(parseClassUrl(fun))
        buffer.append("<PRE>")
        buffer.append(parseAnnotations(fun))
        val start = buffer.length
        buffer.append(parseModifiers(fun))
        buffer.append("def ")
        buffer.append("<b>" + escapeHtml(fun.name) + "</b>")
        buffer.append(parseTypeParameters(fun))
        val end = buffer.length
        buffer.append(parseParameters(fun, end - start - 7))
        buffer.append(parseType(fun))
        buffer.append("</PRE>")
        buffer.append(parseDocComment(fun))

        "<html><body>" + buffer.toString + "</body></html>"
      case decl: ScDeclaredElementsHolder if decl.isInstanceOf[ScValue] || decl.isInstanceOf[ScVariable] =>
        val buffer: StringBuilder = new StringBuilder("")
        decl match {case decl: ScMember => buffer.append(parseClassUrl(decl)) case _ =>}
        buffer.append("<PRE>")
        decl match {
          case an: ScAnnotationsHolder => buffer.append(parseAnnotations(an))
          case _ =>
        }
        decl match {case m: ScModifierListOwner => buffer.append(parseModifiers(m)) case _ =>}
        buffer.append(decl match {case _: ScValue => "val " case _: ScVariable => "var " case _ => ""})
        buffer.append("<b>" + (element match {
          case named: ScNamedElement => escapeHtml(named.name) case _ => "unknown"
        }) + "</b>")
        buffer.append(element match {
          case typed: ScTypedDefinition => parseType(typed)
          case _ => ": Nothing"
        } )
        buffer.append("</PRE>")
        decl match {case doc: ScDocCommentOwner => buffer.append(parseDocComment(doc)) case _ =>}

        "<html><body>" + buffer.toString + "</body></html>"
      case param: ScParameter =>
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append("<PRE>")
        buffer.append(parseAnnotations(param))
        param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ => }
        buffer.append(param match {
          case c: ScClassParameter if c.isVal => "val "
          case c: ScClassParameter if c.isVar => "var "
          case _ => ""
        })
        buffer.append("<b>" + escapeHtml(param.name) + "</b>")
        buffer.append(parseType(param))

        "<html><body>" + buffer.toString + "</body></html>"
      case typez: ScTypeAlias =>
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append(parseClassUrl(typez))

        buffer.append("<PRE>")
        buffer.append(parseAnnotations(typez))
        buffer.append(parseModifiers(typez))
        buffer.append("type <b>" + escapeHtml(typez.name) + "</b>")
        typez match {
          case definition: ScTypeAliasDefinition =>
            buffer.append(" = " +
              urlText(definition.aliasedTypeElement.getType(TypingContext.empty).getOrAny))
          case _ =>
        }
        buffer.append("</PRE>")
        buffer.append(parseDocComment(typez))

        "<html><body>" + buffer.toString + "</body></html>"
      case pattern: ScBindingPattern =>
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append("<PRE>")
        buffer.append("Pattern: ")
        buffer.append("<b>" + escapeHtml(pattern.name) + "</b>")
        buffer.append(parseType(pattern))
        if (pattern.getContext != null)
          pattern.getContext.getContext match {
            case co: PsiDocCommentOwner => buffer.append(parseDocComment(co, withDescription = false))
            case _ =>
          }

        "<html><body>" + buffer.toString + "</body></html>"
      case _ => null
    }
  }

  def findExistingDocComment(contextElement: PsiComment): PsiComment = {
    contextElement match {
      case comment: ScDocComment =>
        val commentOwner = comment.getOwner
        if (commentOwner != null) {
          return commentOwner.getDocComment
        }
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

  private class MacroFinderImpl(comment: ScDocComment, handler: PsiElement => String = {element => element.getText}) extends MacroFinder {
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
          case c => c.getTags.filter(_.getName == MyScaladocParsing.DEFINE_TAG) map {
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
                ScalaPsiUtil.superValsSignatures(named, withSelfType = false) map {
                  case sig => sig.namedElement
                } foreach {
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

  def parseType(elem: ScTypedDefinition)
               (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(": ")
    val typez = elem match {
      case fun: ScFunction => fun.returnType.getOrAny
      case _ => elem.getType(TypingContext.empty).getOrAny
    }
    buffer.append(typeToString(typez))
    buffer.toString()
  }

  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.containingClass
    if (clazz == null) return ""
    "<a href=\"psi_element://" + escapeHtml(clazz.qualifiedName) + "\"><code>" +
      escapeHtml(clazz.qualifiedName) + "</code></a>"
  }

  private def parseParameters(elem: ScParameterOwner, spaces: Int)
                             (implicit typeToString: ScType => String): String = {
    elem.allClauses.map(parseParameterClause(_, spaces)).mkString("\n")
  }

  private def parseParameterClause(elem: ScParameterClause, spaces: Int)
                                  (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(" ")
    for (i <- 1 to spaces) buffer.append(" ")
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    elem.parameters.map(parseParameter(_)).
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
          val paramText = inheritedParams.get(param.name).get.getText
          buffer append leadingAsterisks append paramText.substring(0, paramText.lastIndexOf("\n") + 1)
        } else {
          buffer append leadingAsterisks append PARAM_TAG append " " append param.name append "\n"
        }
      }
    }

    def processTypeParams(owner: ScTypeParametersOwner) {
      for (tparam <- owner.typeParameters) {
        if (inheritedTParams.contains(tparam.name)) {
          val paramText = inheritedTParams.get(tparam.name).get.getText
          buffer.append(leadingAsterisks).append(paramText.substring(0, paramText.lastIndexOf("\n") + 1))
        } else if (inheritedTParams.contains("<" + tparam +">")) {
          val paramTag = inheritedTParams.get("<" + tparam.name + ">").get
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
        val parents = clazz.getSupers

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)
        }

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
          annotation.constructor.args.foreach( a =>
            a.exprs.headOption.map {
              case exprHead => exprHead.getType(TypingContext.empty) match {
                case Success(head, _) =>
                  head match {
                    case ScParameterizedType(_, args) =>
                      args.headOption match {
                        case a: Some[ScType] =>
                          val project = function.getProject
                          implicit val typeSystem = project.typeSystem
                          a.get.extractClass(project) match {
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

  def parseParameter(param: ScParameter, escape: Boolean = true)
                    (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("")
    buffer.append(parseAnnotations(param, ' ', escape))
    param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ =>}
    buffer.append(param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _ => ""
    })
    buffer.append(if (escape) escapeHtml(param.name) else param.name)

    val arrow = ScalaPsiUtil.functionArrow(param.getProject)
    buffer.append(parseType(param)(t => {
      (if (param.isCallByNameParameter) s"$arrow " else "") + typeToString(t)
    }))
    if (param.isRepeatedParameter) buffer.append("*")
    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpressionInSource   match {
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

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    // todo hyperlink identifiers in type bounds
    if (typeParameters.nonEmpty)
      escapeHtml(typeParameters.map(PresentationUtil.presentationString(_)).mkString("[", ", ", "]"))
    else ""
  }

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("extends ")
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        buffer.append(typeToString(seq.head.getType(TypingContext.empty).getOrAny) + "\n")
        for (i <- 1 until seq.length)
          buffer append " with " + typeToString(seq(i).getType(TypingContext.empty).getOrAny)
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
      case null => ""             case ref => ref.resolve match {
        case clazz: PsiClass => "[<a href=\"psi_element://" +
                escapeHtml(clazz.qualifiedName) + "\"><code>" +
                (x.idText match {case Some(text) => text case None => ""}) + "</code></a>]"
        case pack: PsiPackage => "[" + escapeHtml(pack.getQualifiedName) + "]"
        case _ => x.idText match {case Some(text) => "[" + text + "]" case None => ""}
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
      res.append(typeToString(constr.typeElement.getType(TypingContext.empty).getOrAny))

      val attrs = elem.annotationExpr.getAnnotationParameters
      if (attrs.nonEmpty) res append attrs.map(_.getText).mkString("(", ", " ,")")

      res.toString()
    }
    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + sep)
    }
    buffer.toString()
  }


  @tailrec
  private def parseDocComment(elem: PsiDocCommentOwner, withDescription: Boolean = false): String = {
    def getParams(fun: ScParameterOwner): String = {
      fun.parameters.map((param: ScParameter) => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")
    }

    def getTypeParams(fun: ScTypeParametersOwner): String = {
      if (fun.typeParameters.nonEmpty) {
        fun.typeParameters.map(param => escapeHtml(param.name)).mkString("<", " , ", ">")
      } else {
        ""
      }
    }

    Option(elem.getDocComment) match {
      case Some(y: ScDocComment) =>
        val x = replaceWikiWithTags(y)
        val xText = if (x == null) "" else x.getText

        val text = elem match {
          case clazz: ScClass =>
            "\nclass A {\n " + xText + " \npublic " + getTypeParams(clazz) + "void f" +
              getParams(clazz) + " {\n}\n}"
          case typeAlias: ScTypeAlias => xText + "\n class A" + getTypeParams(typeAlias) + " {}"
          case _: ScTypeDefinition => xText + "\nclass A {\n }"
          case f: ScFunction =>
            "class A {\n" + xText + "\npublic " + getTypeParams(f) + "int f" + getParams(f) + " {}\n}"
          case m: PsiMethod =>
            "class A {\n" + m.getText + "\n}"
          case _ => xText + "\nclass A"
        }
        val dummyFile = PsiFileFactory.getInstance(elem.getProject).createFileFromText("dummy", StdFileTypes.JAVA, text).asInstanceOf[PsiJavaFile]
        val javadoc: String = elem match {
          case _: ScFunction | _: ScClass =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _: ScTypeDefinition | _: ScTypeAlias =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
          case _: PsiMethod =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _ => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
        }
        val (s1, s2) = elem.containingClass match {
          case e: PsiClass if withDescription => ("<b>Description copied from class: </b><a href=\"psi_element://" +
            escapeHtml(e.qualifiedName) + "\"><code>" + escapeHtml(e.name) + "</code></a><p>", "</p>")
          case _ => ("", "")
        }
        s1 + (elem match {
          case _: ScFunction | _: ScTypeAlias | _: PsiMethod | _: ScTypeDefinition | _: ScPatternDefinition =>
            val i = javadoc.indexOf("</PRE>")
            javadoc.substring(i + 6, javadoc.length - 14)
          case _ => javadoc.substring(110, javadoc.length - 14)
        }) + s2
      case _ =>
        elem match {
          case fun: ScFunction =>
            fun.superMethod match {
              case Some(fun: PsiMethod) =>
                fun.getNavigationElement match {
                  case fun: PsiMethod =>
                    parseDocComment(fun, withDescription = true)
                  case _ =>
                    parseDocComment(fun, withDescription = true)
                }
              case _ => ""
            }
          case method: PsiMethod =>
            var superSignature: MethodSignatureBackedByPsiMethod = null
            try {
              superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst
            }
            catch {
              case e: IndexNotReadyException =>
            }
            if (superSignature == null) return ""

            val meth = superSignature.getMethod
            meth.getNavigationElement match {
              case fun: PsiMethod =>
                parseDocComment(fun, withDescription = true)
              case _ =>
                parseDocComment(meth, withDescription = true)
            }
          case _ => ""
        }
    }
  }

  private def getWikiTextRepresentation(macroFinder: MacroFinder)(comment: PsiElement): (mutable.StringBuilder, mutable.StringBuilder) = {
    val commentBody = new StringBuilder("")
    val tagsPart = new StringBuilder("")
    var isFirst = true

    def visitTags(element: ScDocTag) {
      element.name match {
        case MyScaladocParsing.TODO_TAG | MyScaladocParsing.NOTE_TAG | MyScaladocParsing.EXAMPLE_TAG =>
          if (isFirst) {
            commentBody.append("<br/><br/>")
            isFirst = false
          }
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi))
          commentBody.append("<br/><br/>")
        case MyScaladocParsing.SEE_TAG =>
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi, commentBody))
          commentBody.append("</dl>")
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
              case MyScaladocParsing.SEE_TAG => result.append("<dl><dt><b>See Also:</b></dt>")

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
            if element.getParent.getParent.getFirstChild.getText == MyScaladocParsing.TYPE_PARAM_TAG  =>
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
            val prefix =  if (element.getParent.getFirstChild == element) "<" else "</"
            result.append(prefix + replaceWikiScheme.get(element.getText).get)
          case _ if element.getParent.getLastChild == element &&                 // do not swap this & last cases
            replaceWikiScheme.contains(element.getParent.getFirstChild.getText) =>
            result.append(element.getText).append("</")
            result.append(replaceWikiScheme.get(element.getParent.getFirstChild.getText).get)
          case ScalaDocTokenType.DOC_COMMENT_END => tagsPart.append(element.getText)
          case ScalaDocTokenType.DOC_MACROS => try {
            macroFinder.getMacroBody(element.getText.stripPrefix("$")).map(a => result append a).getOrElse(result append s"[Cannot find macro: ${element.getText}]")
          } catch {
            case ee: Exception =>
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
    val macroFinder = new MacroFinderImpl(comment.asInstanceOf[ScDocComment], {element =>
      val a = getWikiTextRepresentation(new MacroFinderDummy)(element)
      a._1.result()
    })

    val (commentBody, tagsPart) = getWikiTextRepresentation(macroFinder)(comment)
    val scalaComment = ScalaPsiElementFactory.createScalaFile(commentBody.append("<br/>\n").
      append(tagsPart).toString() + " class a {}", comment.getManager).typeDefinitions.head.getDocComment

    scalaComment
  }

  @tailrec
  private def getDocedElement(originalElement: PsiElement): PsiElement = {
    originalElement match {
      case null => null
      case wrapper: ScFunctionWrapper => wrapper.function
      case _: ScTypeDefinition | _: ScTypeAlias | _: ScValue
           | _: ScVariable | _: ScFunction | _: ScParameter | _: ScBindingPattern => originalElement
      case _ => getDocedElement(originalElement.getParent)
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
    if (i == -1) trimed
    else trimed.substring(0, i) + " ..."
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
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(clazz.getModifierList))
    buffer.append(clazz match {
      case _: ScObject => "object "
      case _: ScClass => "class "
      case _: ScTrait => "trait "
    })
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
        buffer.append(if (i == 1)  "\n  " else " ")
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
      buffer.append(ScalaPsiUtil.getModifiersPresentableText(list))
    }
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function, subst))
    buffer.toString()
  }

  def generateValueInfo(field: ScNamedElement, subst: ScSubstitutor): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(member.getModifierList))
    member match {
      case value: ScValue =>
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst.subst(typed.getType(TypingContext.empty).getOrAny)
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
            val typez = subst.subst(typed.getType(TypingContext.empty).getOrAny)
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
    val typez = subst.subst(subst.subst(binding.getType(TypingContext.empty).getOrAny))
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
        val ttype = subst.subst(d.aliasedType(TypingContext.empty) match {
          case Success(t, _) => t
          case Failure(_, _) => Any
        })
        buffer.append(ttype.presentableText)
      case _ =>
    }
    buffer.toString()
  }

  def generateParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String = {
    val defaultText = s"${parameter.name}: ${subst.subst(parameter.getType(TypingContext.empty).getOrAny).presentableText}"

    (parameter match {
      case clParameter: ScClassParameter =>
        val clazz = PsiTreeUtil.getParentOfType(clParameter, classOf[ScTypeDefinition])

        if (clazz == null) defaultText else clazz.name + " " + clazz.getPresentation.getLocationString + "\n" +
                (if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else "") + clParameter.name +
          ": " + subst.subst(clParameter.getType(TypingContext.empty).getOrAny).presentableText
      case _ => defaultText}) +
        (if (parameter.isRepeatedParameter) "*" else "")
  }
}