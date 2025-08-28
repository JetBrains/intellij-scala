package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.{AnnotationHolder, Annotator, HighlightSeverity}
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.highlighter.ScalaColorsSchemeUtils.NamedArgument
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.SOFT_KEYWORDS
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

class ScalaSyntaxHighlightingAnnotator extends Annotator with DumbAware {
  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = element match {
    case NamedArgument(assignment: ScAssignment) =>
      val parameterRefRange = assignment.leftExpression.getNode.getTextRange
      val start = parameterRefRange.getStartOffset
      val end = assignment.assignmentToken.map(_.endOffset).getOrElse(parameterRefRange.getEndOffset)
      addInfo(TextRange.create(start, end), ScalaHighlightInfoTypes.NAMED_ARGUMENT, holder)

    case annotation: ScAnnotation =>
      addInfo(annotation.getFirstChild, ScalaHighlightInfoTypes.ANNOTATION, holder)
      addInfo(annotation.annotationExpr.constructorInvocation.typeElement, ScalaHighlightInfoTypes.ANNOTATION, holder)

    case parameter: ScParameter  =>
      val nameId = parameter.nameId
      //in scala 3 there are anonymous context parameters which don't have name identifier
      if (nameId != null) {
        val highlightInfo = ScalaColorsSchemeUtils.parameterHighlightInfoType(parameter)
        addInfo(nameId, highlightInfo, holder)
      }

    case typeAlias: ScTypeAlias  =>
      addInfo(typeAlias.nameId, ScalaHighlightInfoTypes.TYPE_ALIAS, holder)

    case e if isSoftKeyword(e) =>
      addInfo(e, ScalaHighlightInfoTypes.KEYWORD, holder)

    case e if e.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
      ScalaColorsSchemeUtils
        .findHighlightInfoTypeByParent(e)
        .foreach(a => addInfo(e, a, holder))

    case _ =>
  }

  private def addInfo(e: PsiElement, highlightInfoType: HighlightInfoType, holder: AnnotationHolder): Unit =
    holder.newSilentAnnotation(highlightInfoType.getSeverity(e))
      .textAttributes(highlightInfoType.getAttributesKey)
      .range(e)
      .create()

  //noinspection SameParameterValue
  private def addInfo(range: TextRange, highlightInfoType: HighlightInfoType, holder: AnnotationHolder): Unit =
    holder.newSilentAnnotation(highlightInfoType.getSeverity(null))
      .textAttributes(highlightInfoType.getAttributesKey)
      .range(range)
      .create()

  private def isSoftKeyword(element: PsiElement): Boolean =
    SOFT_KEYWORDS.contains(element.getNode.getElementType)
}
