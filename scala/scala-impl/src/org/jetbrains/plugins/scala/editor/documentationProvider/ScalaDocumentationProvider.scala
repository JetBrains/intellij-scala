package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._
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
      ScalaDocStubGenerator.createScalaDocStub(scalaDocComment.getOwner)
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
