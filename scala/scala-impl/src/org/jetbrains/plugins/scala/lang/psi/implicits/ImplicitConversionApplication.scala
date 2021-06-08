package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class ImplicitConversionApplication(resultType: ScType,
                                         implicitParameters: Seq[ScalaResolveResult] = Seq.empty)