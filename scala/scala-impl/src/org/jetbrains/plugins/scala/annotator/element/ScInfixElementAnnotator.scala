package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.editor.ScalaIndentationSyntaxUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInfixElement


object ScInfixElementAnnotator extends ElementAnnotator[ScInfixElement] {

  override def annotate(infix: ScInfixElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val source3Opts = infix.source3Options
    if (source3Opts.isSource3Enabled && !source3Opts.leadingInfix && isLeadingInfixOperator(infix.operation)) {
      holder.createErrorAnnotation(
        infix.operation,
        ScalaBundle.message("leading.infix.operators.are.not.available.with.only...")
      )
    }
  }

  private def isLeadingInfixOperator(element: PsiElement): Boolean =
    ScalaIndentationSyntaxUtils.precededIndentWhitespace(element).isDefined
}
