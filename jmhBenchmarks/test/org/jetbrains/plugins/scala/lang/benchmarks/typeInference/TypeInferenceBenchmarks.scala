package org.jetbrains.plugins.scala.lang.benchmarks.typeInference

/**
  * @author Nikolay.Tropin
  */
class AmbigousConversion extends TypeInferenceBenchmarkBase("AmbigousConversion")
class ConstructorPatternComplex extends TypeInferenceBenchmarkBase("ConstructorPatternComplex")
class IncompleteForStatement extends TypeInferenceBenchmarkBase("IncompleteForStatement")
class RawTypes extends TypeInferenceBenchmarkBase("RawTypes")
class Flatten extends TypeInferenceBenchmarkBase("Flatten")
class MapLengthInfix extends TypeInferenceBenchmarkBase("MapLengthInfix")
class VarargTypeInference extends TypeInferenceBenchmarkBase("VarargTypeInference")
class WrongArgumentType extends TypeInferenceBenchmarkBase("WrongArgumentType")
class ShapelessLike extends TypeInferenceBenchmarkBase("ShapelessLike")
class ToArray extends TypeInferenceBenchmarkBase("ToArray")
class UnapplySeqLocalTypeInference extends TypeInferenceBenchmarkBase("UnapplySeqLocalTypeInference")
class UnapplySeqWithImplicitParam extends TypeInferenceBenchmarkBase("UnapplySeqWithImplicitParam")

class SprayRouting extends TypeInferenceBenchmarkBase("SprayRouting") {
  override protected def additionalLibraries(): Array[String] = Array("spray")
}

class Scalaz extends TypeInferenceBenchmarkBase("Scalaz") {
  override protected def additionalLibraries(): Array[String] = Array("scalaz")
}

class Slick extends TypeInferenceBenchmarkBase("Slick") {
  override protected def additionalLibraries(): Array[String] = Array("slick")
}

class Cats extends TypeInferenceBenchmarkBase("Cats") {
  override protected def additionalLibraries(): Array[String] = Array("cats")
}
