package org.jetbrains.jps.incremental.scala.data

import scala.util.Try

/**
  * @author Nikolay.Tropin
  */
case class SbtIncrementalOptions(nameHashing: Boolean, recompileOnMacroDef: Boolean, transitiveStep: Int, recompileAllFraction: Double) {
  def asString: String = s"$nameHashing;$recompileOnMacroDef;$transitiveStep;$recompileAllFraction"

  def nonDefault = {
    val names = Seq("nameHashing", "recompileOnMacroDef", "transitiveStep", "recompileAllFraction")
    val values = this.productIterator.toSeq
    val defaultValues = SbtIncrementalOptions.Default.productIterator.toSeq
    val differs = for {
      ((name, value), defaultValue) <- names.zip(values).zip(defaultValues)
      if value != defaultValue
    } yield s"$name = $value"
    differs.mkString(", ")
  }
}

object SbtIncrementalOptions {
  val Default = SbtIncrementalOptions(nameHashing = true, recompileOnMacroDef = true, transitiveStep = 3, recompileAllFraction = 0.5)

  def fromString(s: String): Option[SbtIncrementalOptions] = {
    Try {
      val Array(nameHashing, recompileOnMacroDef, transitiveStep, recompileAllFraction) = s.split(';')
      SbtIncrementalOptions(nameHashing.toBoolean, recompileOnMacroDef.toBoolean, transitiveStep.toInt, recompileAllFraction.toDouble)
    }.toOption
  }
}