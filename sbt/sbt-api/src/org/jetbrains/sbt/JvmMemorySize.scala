package org.jetbrains.sbt

import org.jetbrains.annotations.NonNls

import scala.util.matching.Regex

case class JvmMemorySize private(sizeInBytes: Long)(val sizeString: String) extends Ordered[JvmMemorySize] {
  override def compare(t: JvmMemorySize): Int =
    sizeInBytes compare t.sizeInBytes

  override def toString: String = sizeString
}

object JvmMemorySize {
  abstract class Factory(powK: Int, val unitSuffix: String) {
    val byteMultiplier: Long = Iterator.fill(powK)(1024L).product

    def apply(size: Long): JvmMemorySize =JvmMemorySize(size * byteMultiplier)(size.toString + unitSuffix)
  }

  object Bytes extends Factory(0, "")
  object Kilobytes extends Factory(1, "K")
  object Megabytes extends Factory(2, "M")
  object Gigabytes extends Factory(3, "G")
  object Terabytes extends Factory(4, "T")

  // units are defined in function `atomull`
  // See http://hg.openjdk.java.net/jdk8u/jdk8u/hotspot/file/tip/src/share/vm/runtime/arguments.cpp#l576
  val units: Seq[Factory] = Seq(Bytes, Kilobytes, Megabytes, Gigabytes, Terabytes)


  private val unitMultiplierMapping: Map[String, Long] =
    units.map(unit => unit.unitSuffix -> unit.byteMultiplier).toMap

  @NonNls private val sizeWithUnit: Regex = raw"(\d+)([a-zA-Z]*)".r

  def parse(sizeString: String): Option[JvmMemorySize] = for {
    case sizeWithUnit(sizeStr, unitStr) <- Some(sizeString)
    unitMultiplier <- unitMultiplierMapping.get(unitStr.toUpperCase)
    size = sizeStr.toLong
    sizeInBytes = size * unitMultiplier
  } yield JvmMemorySize(sizeInBytes)(sizeString)
}
