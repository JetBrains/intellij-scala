package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, HighlightVisitor}
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.ScalaColorsSchemeUtils.NamedArgument
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.SOFT_KEYWORDS
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class ScalaSyntaxHighlightingVisitor extends HighlightVisitor with DumbAware {
  private var holder: HighlightInfoHolder = _

  override def suitableForFile(file: PsiFile): Boolean =
    file.is[ScFile]

  override def analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean = {
    this.holder = holder
    try {
      action.run()
    } finally {
      this.holder = null
    }
    true
  }

  override def visit(element: PsiElement): Unit = element match {
    case NamedArgument(assignment: ScAssignment) =>
      val parameterRefRange = assignment.leftExpression.getNode.getTextRange
      val start = parameterRefRange.getStartOffset
      val end = assignment.assignmentToken.map(_.endOffset).getOrElse(parameterRefRange.getEndOffset)
      holder.add(info(TextRange.create(start, end), ScalaHighlightInfoTypes.NAMED_ARGUMENT))

    case annotation: ScAnnotation =>
      holder.add(info(annotation.getFirstChild, ScalaHighlightInfoTypes.ANNOTATION))
      holder.add(info(annotation.annotationExpr.constructorInvocation.typeElement, ScalaHighlightInfoTypes.ANNOTATION))

    case parameter: ScParameter  =>
      val nameId = parameter.nameId
      //in scala 3 there are anonymous context parameters which don't have name identifier
      if (nameId != null) {
        val attributesKey = ScalaColorsSchemeUtils.parameterHighlightInfoType(parameter)
        holder.add(info(nameId, attributesKey))
      }

    case typeAlias: ScTypeAlias  =>
      holder.add(info(typeAlias.nameId, ScalaHighlightInfoTypes.TYPE_ALIAS))

    case e if isSoftKeyword(e) =>
      holder.add(info(e, ScalaHighlightInfoTypes.KEYWORD))

    case e if e.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
      ScalaColorsSchemeUtils
        .findHighlightInfoTypeByParent(e)
        .foreach(a => holder.add(info(e, a)))

    case _ =>
  }

  private def info(e: PsiElement, highlightInfoType: HighlightInfoType): HighlightInfo =
    info(e.getTextRange, highlightInfoType)

  private def info(range: TextRange, highlightInfoType: HighlightInfoType): HighlightInfo = {
    HighlightInfo.newHighlightInfo(highlightInfoType)
      .range(range)
      .create()
  }

  private def isSoftKeyword(element: PsiElement): Boolean =
    SOFT_KEYWORDS.contains(element.getNode.getElementType)

  override def clone(): HighlightVisitor =
    new ScalaSyntaxHighlightingVisitor()
}
