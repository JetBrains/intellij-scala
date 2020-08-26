package org.jetbrains.plugins.scala.lang.parameterInfo

import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature

abstract class ScalaParameterInfoEnhancer {
  def enhance(signature: PhysicalMethodSignature, arguments: collection.Seq[ScExpression]): collection.Seq[PhysicalMethodSignature]
}

object ScalaParameterInfoEnhancer
  extends ExtensionPointDeclaration[ScalaParameterInfoEnhancer](
    "org.intellij.scala.parameterInfoEnhancer"
  ) {

  def enhance(signature: PhysicalMethodSignature, arguments: collection.Seq[ScExpression]): collection.Seq[PhysicalMethodSignature] =
    implementations.flatMap(_.enhance(signature, arguments))
}
