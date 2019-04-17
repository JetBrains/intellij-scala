package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

object ScParameterizedTypeElementAnnotator extends ElementAnnotator[ScParameterizedTypeElement] {
  override def annotate(element: ScParameterizedTypeElement, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    val typeParamOwner = element.typeElement.getTypeNoConstructor.toOption
      .flatMap(_.extractDesignated(expandAliases = false))
      .collect {case t: ScTypeParametersOwner => t}
    typeParamOwner.foreach { t =>
      val typeParametersLength = t.typeParameters.length
      val argsLength = element.typeArgList.typeArgs.length
      if (typeParametersLength != argsLength) {
        val error = "Wrong number of type parameters. Expected: " + typeParametersLength + ", actual: " + argsLength
        val leftBracket = element.typeArgList.getNode.findChildByType(ScalaTokenTypes.tLSQBRACKET)
        if (leftBracket != null) {
          val annotation = holder.createErrorAnnotation(leftBracket, error)
          annotation.setHighlightType(ProblemHighlightType.ERROR)
        }
        val rightBracket = element.typeArgList.getNode.findChildByType(ScalaTokenTypes.tRSQBRACKET)
        if (rightBracket != null) {
          val annotation = holder.createErrorAnnotation(rightBracket, error)
          annotation.setHighlightType(ProblemHighlightType.ERROR)
        }
      }
    }
  }
}
