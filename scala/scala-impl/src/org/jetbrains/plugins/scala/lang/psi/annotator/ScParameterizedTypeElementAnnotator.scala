package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

trait ScParameterizedTypeElementAnnotator extends Annotatable { self: ScParameterizedTypeElement =>
  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    val typeParamOwner = typeElement.getTypeNoConstructor.toOption
      .flatMap(_.extractDesignated(expandAliases = false))
      .collect {case t: ScTypeParametersOwner => t}
    typeParamOwner.foreach { t =>
      val typeParametersLength = t.typeParameters.length
      val argsLength = typeArgList.typeArgs.length
      if (typeParametersLength != argsLength) {
        val error = "Wrong number of type parameters. Expected: " + typeParametersLength + ", actual: " + argsLength
        val leftBracket = typeArgList.getNode.findChildByType(ScalaTokenTypes.tLSQBRACKET)
        if (leftBracket != null) {
          val annotation = holder.createErrorAnnotation(leftBracket, error)
          annotation.setHighlightType(ProblemHighlightType.ERROR)
        }
        val rightBracket = typeArgList.getNode.findChildByType(ScalaTokenTypes.tRSQBRACKET)
        if (rightBracket != null) {
          val annotation = holder.createErrorAnnotation(rightBracket, error)
          annotation.setHighlightType(ProblemHighlightType.ERROR)
        }
      }
    }
  }
}
