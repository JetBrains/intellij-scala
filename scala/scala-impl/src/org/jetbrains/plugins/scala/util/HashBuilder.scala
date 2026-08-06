package org.jetbrains.plugins.scala.util

import scala.language.implicitConversions

/**
 * Utility for allocation-free computation of hash code, returns the same value as java.util.Objects.hash
 */
class HashBuilder(val value: Int) extends AnyVal {
  final def #+(other: Int)     = new HashBuilder(31 * value + other)
  final def #+(other: Boolean) = new HashBuilder(31 * value + other.##)
  final def #+(other: Long)    = new HashBuilder(31 * value + other.##)
  final def #+(other: Double)  = new HashBuilder(31 * value + other.##)
  final def #+(other: Any)     = new HashBuilder(31 * value + other.##)
}

object HashBuilder {
  implicit final def toHashBuilder(v: Int)    : HashBuilder = new HashBuilder(31 + v)
  implicit final def toHashBuilder(v: Long)   : HashBuilder = new HashBuilder(31 + v.##)
  implicit final def toHashBuilder(v: Boolean): HashBuilder = new HashBuilder(31 + v.##)
  implicit final def toHashBuilder(v: Double) : HashBuilder = new HashBuilder(31 + v.##)
  implicit final def toHashBuilder(v: AnyRef) : HashBuilder = new HashBuilder(31 + v.##)

  implicit final def toInt(builder: HashBuilder): Int = builder.value
}