package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * Type element representing Scala 3 type lambdas.
 * e.g. `[X] =>> F[X]` or `[A >: Foo <: Bar, B] =>> [C] =>> (A, B, C)`
 */
trait ScTypeLambdaTypeElement extends ScTypeElement with ScTypeParametersOwner {
  override protected val typeName: String = "TypeLambda"

  def resultTypeElement: Option[ScTypeElement]
  def resultType: TypeResult
}

object ScTypeLambdaTypeElement {
  def unapplySeq(lambda: ScTypeLambdaTypeElement): Some[(Option[ScTypeElement], Seq[ScTypeParam])] =
    Some(lambda.resultTypeElement, lambda.typeParameters)
}
