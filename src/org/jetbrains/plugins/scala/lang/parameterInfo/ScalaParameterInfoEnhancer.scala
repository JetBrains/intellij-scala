package org.jetbrains.plugins.scala.lang.parameterInfo

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature

/**
  * @author Alefas
  * @since 25/08/16
  */
abstract class ScalaParameterInfoEnhancer {
  def enhance(signature: PhysicalSignature, arguments: Seq[ScExpression]): Seq[PhysicalSignature]

  def restartHint(element: PsiElement, position: Int): Boolean
}

object ScalaParameterInfoEnhancer {
  val EP_NAME = ExtensionPointName.create[ScalaParameterInfoEnhancer]("org.intellij.scala.parameterInfoEnhancer")

  def enchancers: Seq[ScalaParameterInfoEnhancer] = EP_NAME.getExtensions
}
