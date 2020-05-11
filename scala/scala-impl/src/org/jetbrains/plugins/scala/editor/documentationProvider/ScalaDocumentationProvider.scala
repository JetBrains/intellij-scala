package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

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
      case el: ScalaLookupItem => el.getPsiElement
      case element: PsiElement => element
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

    ScalaDocGenerator.generateDoc(elementWithDoc)
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
