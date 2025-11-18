package types

trait Constant {
  final val v1 = 2147483647

  final val v2 = 9223372036854775807L

  final val v3 = 3.4028235E38F

  final val v4 = 1.7976931348623157E308D

  final val v5 = true

  final val v6 = false

  final val v7 = 'c'

  final val v7a = '\n'

  final val v8 = "String"

  final val v8a = "\n"

  val v9/**//*: Int*/ = /**/123/*???*/

  val v10: 123 = ???

  final val FloatPositiveInfinity = java.lang.Float.POSITIVE_INFINITY

  final val FloatNegativeInfinity = java.lang.Float.NEGATIVE_INFINITY

  final val FloatNaN = java.lang.Float.NaN

  final val DoublePositiveInfinity = java.lang.Double.POSITIVE_INFINITY

  final val DoubleNegativeInfinity = java.lang.Double.NEGATIVE_INFINITY

  final val DoubleNaN = java.lang.Double.NaN

  final val CharMinValue = Character.MIN_VALUE

  final val CharMaxValue = Character.MAX_VALUE
}