package org.jetbrains.jps.incremental.scala.data

import scala.util.Try

/**
  * @author Nikolay.Tropin
  */
case class SbtIncrementalOptions(nameHashing: Boolean, recompileOnMacroDef: Boolean, transitiveStep: Int, recompileAllFraction: Double) {
  def asString: String = s"$nameHashing;$recompileOnMacroDef;$transitiveStep;$recompileAllFraction"
}

object SbtIncrementalOptions {
  def fromString(s: String): Option[SbtIncrementalOptions] = {
    Try {
      val Array(nameHashing, recompileOnMacroDef, transitiveStep, recompileAllFraction) = s.split(';')
      SbtIncrementalOptions(nameHashing.toBoolean, recompileOnMacroDef.toBoolean, transitiveStep.toInt, recompileAllFraction.toDouble)
    }.toOption
  }
}