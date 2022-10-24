package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ConvertToExplicitSymbolQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

object ScSymbolLiteralAnnotator extends ElementAnnotator[ScSymbolLiteral] {

  override def annotate(element: ScSymbolLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {

    if (element.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_2_13) {
      holder.createWarningAnnotation(
        element,
        ScalaBundle.message("symbolliterals.are.deprecated", element.contentText),
        ProblemHighlightType.LIKE_DEPRECATED,
        new ConvertToExplicitSymbolQuickFix(element)
      )
    }
  }
}