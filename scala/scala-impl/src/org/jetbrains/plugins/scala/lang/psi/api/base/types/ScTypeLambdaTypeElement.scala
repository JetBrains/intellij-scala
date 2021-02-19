package org.jetbrains.plugins.scala.lang.psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPolymorphicElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * Type element representing Scala 3 type lambdas.
 * e.g. `[X] =>> F[X]` or `[+A >: Foo <: Bar, -B] =>> [C] =>> (A, B, C)`
 */
trait ScTypeLambdaTypeElementBase extends ScTypeElementBase { this: ScTypeLambdaTypeElement =>
  override protected val typeName: String = "TypeLambda"

  def resultTypeElement: Option[ScTypeElement]
  def resultType: TypeResult
}

abstract class ScTypeLambdaTypeElementCompanion {
  def unapplySeq(lambda: ScTypeLambdaTypeElement): Some[(Option[ScTypeElement], Seq[ScTypeParam])] =
    Some(lambda.resultTypeElement, lambda.typeParameters)
}