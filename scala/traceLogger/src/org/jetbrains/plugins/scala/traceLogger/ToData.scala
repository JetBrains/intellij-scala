package org.jetbrains.plugins.scala.traceLogger

/**
 * ToData controls how types are serialized into Data.
 * For now, Data is just a String, but we might want to change that
 * in the future.
 *
 * You can provide an implicit ToData for your own type to customize
 * serialization.
 *
 * There is a very low priority ToData[T] that will convert any type T
 * into Data by just calling toString as a fallback.
 *
 * If you want to provide a ToData for a type and all its subtypes
 * implement it like
 * {{{
 *   implicit final def yourTypeAsData[T <: YourType]: ToData[T] =
 *     (value: YourType) => ...
 * }}}
 *
 * You can then provide more specific implementations for a subtype
 * by defining a normal implicit ToData-val or use another generic
 * functions to also cover all its subtypes.
 */
trait ToData[T] {
  def toData(value: T): Data
}

object ToData extends HighPriorityToDataImplicits {
  def apply[T](value: T)(implicit converter: ToData[T]): Data =
    converter.toData(value)

  final case class Raw(data: Data) extends AnyVal

  implicit final val rawAsData: ToData[Raw] = raw => raw.data

  def raw[T: ToData](value: T): Raw = Raw(ToData(value))
}

trait HighPriorityToDataImplicits extends LowPriorityToDataImplicits {
  implicit final val stringAsData: ToData[String] = (value: String) => "\"" + value + "\""
  implicit final def arrayAsData[T: ToData]: ToData[Array[T]] =
    (value: Array[T]) => value.iterator.map(ToData.apply).mkString("Array(", ", ", ")")
}

trait LowPriorityToDataImplicits {
  implicit final def toStringResultAsData[T]: ToData[T] = (value: Any) => value.toString
}