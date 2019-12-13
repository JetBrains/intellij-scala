package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.annotator.quickfix.ConvertToExplicitSymbolQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

object ScSymbolLiteralAnnotator extends ElementAnnotator[ScSymbolLiteral] {

  override def annotate(element: ScSymbolLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {

    if (element.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_2_13) {
      val annotation = holder.createWarningAnnotation(element, ScalaBundle.message("symbolliterals.are.deprecated", element.contentText))
      annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
      annotation.registerFix(new ConvertToExplicitSymbolQuickFix(element))
    }
  }
}