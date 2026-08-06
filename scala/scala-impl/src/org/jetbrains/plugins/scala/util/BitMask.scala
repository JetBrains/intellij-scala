package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.util.BitMask.bitsNeededToRepresent

import java.lang.{Integer => JInt}
import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag
import scala.util.hashing.MurmurHash3

trait BitMask extends Any {
  type T

  def mask: Int

  def chunkSize: Int
  def pos: Int

  def read(bits: Int): T
  def write(bits: Int, value: T): Int
  final def write(bits: Ref[Int], value: T): Unit =
    bits.set(write(bits.get(), value))

  def fingerprintString: String = toString
}

object BitMask {
  def bitsNeededToRepresent(n: Int): Int = {
    JInt.SIZE - JInt.numberOfLeadingZeros(n)
  }

  private def makeMask(pos: Int, chunkSize: Int): Int = {
    assert(chunkSize > 0)
    assert(pos + chunkSize <= JInt.SIZE, s"Do not have space for $chunkSize bits at pos $pos")
    val fullInt = ~0
    val baseMask = fullInt >>> (JInt.SIZE - chunkSize)
    assert(JInt.bitCount(baseMask) == chunkSize)
    baseMask << pos
  }

  final class Bool private(override val mask: Int) extends AnyVal with BitMask {
    type T = Boolean

    override def chunkSize: Int = 1
    override def pos: Int = JInt.numberOfTrailingZeros(mask)

    @inline
    override def read(bits: Int): Boolean =
      (bits & mask) != 0
    override def write(bits: Int, bool: Boolean): Int =
      (bits & ~mask) | (if (bool) mask else 0)

    override def toString: String = s"Bool($pos)"
  }

  object Bool {
    def apply(pos: Int): Bool = new Bool(makeMask(pos, chunkSize = 1))
      .ensuring(_.pos == pos).ensuring(m => JInt.bitCount(m.mask) == 1)
    def unapply(bool: Bool): Some[Int] = Some(bool.pos)
  }

  final case class Nat(override val pos: Int, max: Int) extends BitMask {
    assert(max > 0)

    type T = Int

    override val chunkSize: Int = bitsNeededToRepresent(max)
    override val mask: Int = makeMask(pos, chunkSize)

    @inline
    override def read(value: Int): Int = (value & mask) >>> pos
    override def write(target: Int, value: Int): Int = {
      assert(value >= 0 && value <= max)
      (target & ~mask) | (value << pos)
    }
  }

  final case class Integer(override val pos: Int, min: Int, max: Int) extends BitMask {
    assert(min < max)
    type T = Int

    val shiftedMax: Int = max - min

    override val chunkSize: Int = bitsNeededToRepresent(shiftedMax)
    override val mask: Int = makeMask(pos, chunkSize)

    @inline
    override def read(bits: Int): Int = ((bits & mask) >>> pos) + min
    override def write(bits: Int, value: Int): Int = {
      assert(value >= min && value <= max)
      (bits & ~mask) | ((value - min) << pos)
    }
  }

  final class JEnum[E <: Enum[E]: ClassTag](override val pos: Int) extends BitMask {
    type T = E
    val enumClass: Class[E] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[E]]
    val constants: ArraySeq[E] = enumClass.getEnumConstants.to(ArraySeq)
    private val inner = Nat(pos, constants.size)

    override val mask: Int = inner.mask
    override def chunkSize: Int = inner.chunkSize

    @inline
    override def read(bits: Int): E = constants(inner.read(bits))
    override def write(bits: Int, value: E): Int = inner.write(bits, value.ordinal())

    override def toString: String = s"JEnum[${enumClass.getSimpleName}]($pos)"
    override def fingerprintString: String = s"$toString with {${constants.mkString(", ")}}"
  }

  object JEnum {
    def apply[E <: Enum[E]: ClassTag](pos: Int): JEnum[E] = new JEnum(pos)
  }
}

trait BitMaskStorage {
  private var finished: Boolean = false
  private var usedBits: Int = 0
  private var _members: Map[String, BitMask] = Map.empty

  def members: Map[String, BitMask] = _members

  private def nextPos: Int = bitsNeededToRepresent(usedBits)

  private def create[T <: BitMask](f: Int => T, name: String): T = {
    assert(!finished)
    val m = f(nextPos)
    assert((m.mask & usedBits) == 0)
    usedBits |= m.mask
    _members += name -> m
    m
  }

  protected def bool(name: String): BitMask.Bool = create(BitMask.Bool(_), name)
  protected def nat(max: Int, name: String): BitMask.Nat = create(BitMask.Nat(_, max), name)
  protected def int(min: Int, max: Int, name: String): BitMask.Integer = create(BitMask.Integer(_, min, max), name)
  protected def jEnum[E <: Enum[E]: ClassTag](name: String): BitMask.JEnum[E] = create(BitMask.JEnum(_), name)

  val version: Int

  protected def finishWithoutVersion(): Unit = {
    assert(members.nonEmpty, "Empty BitMaskSet?")
    assert(!finished)
    finished = true
  }

  protected def finishAndMakeVersion(): Int = {
    finishWithoutVersion()

    val fingerprint = fingerprintString
    MurmurHash3.stringHash(fingerprint)
  }

  def fingerprintString: String = {
    val fingerprintBuilder = new StringBuilder

    fingerprintBuilder.append("VersionFields:")

    for ((name, mask) <- members.toSeq.sortBy(_._1))
      fingerprintBuilder.append(s"$name -> ${mask.fingerprintString}\n")

    fingerprintBuilder.result()
  }
}