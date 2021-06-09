package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScPattern, ScPatternArgumentList, ScSeqWildcardPattern}
import org.jetbrains.plugins.scala.macroAnnotations.Measure

object ScPatternArgumentListAnnotator extends ElementAnnotator[ScPatternArgumentList] {

  @Measure
  override def annotate(element: ScPatternArgumentList, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val iterator = element.patterns.iterator

    def process(pattern: ScPattern): Unit =
      pattern match {
        case seqWildcard: ScSeqWildcardPattern if iterator.hasNext=>
          val annotation = holder.createWarningAnnotation(seqWildcard, ScalaBundle.message("vararg.pattern.must.be.last.pattern"))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
        case _ =>
      }

    iterator.foreach {
      case naming: ScNamingPattern => process(naming.named)
      case other                   => process(other)
    }
  }
}
