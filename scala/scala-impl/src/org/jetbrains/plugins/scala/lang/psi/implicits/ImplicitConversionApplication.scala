package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class ImplicitConversionApplication(resultType: ScType,
                                         substitutor: ScSubstitutor = ScSubstitutor.empty,
                                         implicitParameters: collection.Seq[ScalaResolveResult] = Seq.empty)