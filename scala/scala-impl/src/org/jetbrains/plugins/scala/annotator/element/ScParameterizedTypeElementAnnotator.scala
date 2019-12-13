package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

object ScParameterizedTypeElementAnnotator extends ElementAnnotator[ScParameterizedTypeElement] {

  override def annotate(element: ScParameterizedTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
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
