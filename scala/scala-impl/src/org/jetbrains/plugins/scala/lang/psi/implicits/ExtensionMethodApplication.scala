package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final case class ExtensionMethodApplication(resultType: ScType,
                                            implicitParameters: Seq[ScalaResolveResult] = Seq.empty)
