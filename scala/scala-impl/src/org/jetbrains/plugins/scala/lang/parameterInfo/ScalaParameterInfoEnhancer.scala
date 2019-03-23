package org.jetbrains.plugins.scala.lang.parameterInfo

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature

/**
  * @author Alefas
  * @since 25/08/16
  */
abstract class ScalaParameterInfoEnhancer {
  def enhance(signature: PhysicalMethodSignature, arguments: Seq[ScExpression]): Seq[PhysicalMethodSignature]
}

object ScalaParameterInfoEnhancer {
  val EP_NAME: ExtensionPointName[ScalaParameterInfoEnhancer] =
    ExtensionPointName.create("org.intellij.scala.parameterInfoEnhancer")

  def enhancers: Seq[ScalaParameterInfoEnhancer] = EP_NAME.getExtensions

  def enhance(signature: PhysicalMethodSignature, arguments: Seq[ScExpression]): Seq[PhysicalMethodSignature] = {
    enhancers.flatMap(_.enhance(signature, arguments))
  }
}
