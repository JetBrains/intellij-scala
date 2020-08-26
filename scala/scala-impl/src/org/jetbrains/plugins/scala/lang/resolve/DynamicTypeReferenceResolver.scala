package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

abstract class DynamicTypeReferenceResolver {

  def resolve(expression: ScReferenceExpression): Array[ResolveResult]
}

object DynamicTypeReferenceResolver
  extends ExtensionPointDeclaration[DynamicTypeReferenceResolver](
    "org.intellij.scala.scalaDynamicTypeResolver"
  ) {

  def getAllResolveResult(expression: ScReferenceExpression): collection.Seq[ResolveResult] =
    implementations.flatMap(_.resolve(expression))
}
