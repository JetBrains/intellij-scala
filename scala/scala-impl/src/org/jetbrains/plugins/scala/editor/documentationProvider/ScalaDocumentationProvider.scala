package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, ScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import java.util.function.Consumer
import scala.annotation.tailrec

class ScalaDocumentationProvider extends CodeDocumentationProvider {

  override def getDocumentationElementForLookupItem(
    psiManager: PsiManager,
    obj: Object,
    element: PsiElement
  ): PsiElement =
    obj match {
      case (_, element: PsiElement, _) => element
      case el: ScalaLookupItem         => el.getPsiElement
      case element: PsiElement         => element
      case _                           => null
    }

  override def getUrlFor(element: PsiElement, originalElement: PsiElement): java.util.List[String] = null

  override def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    if (!isInScalaFile(element))
      null
    else {
      val result = ScalaDocQuickInfoGenerator.getQuickNavigateInfo(element, originalElement)
      result.map(HintUtil.prepareHintText(_, HintUtil.getInformationHint)).orNull
    }
  }

  override def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement =
    if (!isInScalaFile(context)) null
    else JavaDocUtil.findReferenceTarget(psiManager, link, context) match {
      case null                        => findScalaReferenceTarget(psiManager, link, context).orNull
      case PsiClassWrapper(definition) => definition
      case other                       => other
    }

  private def findScalaReferenceTarget(psiManager: PsiManager, link: String, context: PsiElement): Option[PsiElement] = {
    val scalaPsiManager = ScalaPsiManager.instance(psiManager.getProject)
    val scope = context.containingFile.map(_.resolveScope)
    scope.flatMap { s =>
      val res1 = scalaPsiManager.getCachedClass(s, link)
      val res2 = res1.orElse(scalaPsiManager.getStableAliasesByFqn(link, s).headOption)
      res2
    }
  }

  override def generateDoc(element: PsiElement, @Nullable originalElement: PsiElement): String = {
    if (!isInScalaFile(element)) {
      if (element.is[ScalaPsiElement])
        debugMessage("Asked to build doc for a scala element, but it is in non scala file (1)", element)

      return null
    }

    val elementWithDoc = getElementWithDoc(element)
    if (elementWithDoc == null) {
      debugMessage("No actual doc owner found for element (2)", element)
      return null
    }

    ScalaDocGenerator.generateDoc(elementWithDoc, Option(originalElement))
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
      elem.withParents.findByType[ScDocCommentOwner]

    val docOwner = Option(startPoint).flatMap(findDocCommentOwner)
    docOwner.map(d => Pair.create(d, d.getDocComment).asInstanceOf[Pair[PsiElement, PsiComment]]).orNull
  }

  override final def generateRenderedDoc(comment: PsiDocCommentBase): String =
    comment match {
      case scalaComment: ScDocComment  =>
        scalaComment.getOwner match {
          case scalaCommentOwner: ScDocCommentOwner =>
            ScalaDocGenerator.generateDocRendered(scalaCommentOwner, scalaComment)
          case _ => super.generateRenderedDoc(comment)
        }
      case _ => super.generateRenderedDoc(comment)
    }

  override final def collectDocComments(file: PsiFile, sink: Consumer[_ >: PsiDocCommentBase]): Unit = {
    val scalaFile: ScalaFile = file match {
      case sf: ScalaFile => sf
      case _             => return
    }
    scalaFile
      .breadthFirst {
        case _: PsiComment => false // early break, do not go inside comments itself
        case _             => true
      }
      .foreach {
        case docOwner: ScDocCommentOwner =>
          docOwner.docComment.foreach(sink.accept)
        case _ =>
      }
  }
}

object ScalaDocumentationProvider {

  private def isInScalaFile(element: PsiElement): Boolean =
    element != null && element.getContainingFile.is[ScalaFile]

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider")

  private def debugMessage(@NonNls msg: String, elem: PsiElement): Unit = {
    val footer = if (!elem.isValid) {
      s"[Invalid Element: ${elem.getNode} ${elem.getClass.getName}]"
    } else if (elem.getContainingFile == null) {
      s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: NULL]"
    } else {
      s"[Element: ${elem.getNode} ${elem.getClass.getName}] [File: ${elem.getContainingFile.name}] [Language: ${elem.getContainingFile.getLanguage}]"
    }

    LOG.debug(s"[ScalaDocProvider] [ $msg ] $footer")
  }

  @tailrec
  private def getElementWithDoc(originalElement: PsiElement): PsiElement = {
    originalElement match {
      case null                        => null
      case ScFunctionWrapper(delegate) => delegate
      //Q: why can't we use `ScDocCommentOwner`?
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
}
