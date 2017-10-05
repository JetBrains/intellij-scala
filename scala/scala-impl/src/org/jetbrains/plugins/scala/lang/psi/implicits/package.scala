package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Nikolay.Tropin
  */
package object implicits {
  type Candidate = (ScalaResolveResult, ScSubstitutor)
}
