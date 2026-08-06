package org.jetbrains.plugins.scala.decompiler.scalasig

import java.nio.charset.StandardCharsets

import scala.annotation.tailrec

//Based on scala.reflect.internal.pickling.PickleBuffer
class ScalaSigReader(bytes: Array[Byte]) {
  var readIndex = 0

  skipVersion()

  /** Read a byte */
  def readByte(): Int = {
    val x = bytes(readIndex).toInt; readIndex += 1; x
  }

  /** Read a natural number in big endian format, base 128.
    *  All but the last digits have bit 0x80 set.*/
  def readNat(): Int = readLongNat().toInt

  def readLongNat(): Long = {
    var b = 0L
    var x = 0L
    do {
      b = readByte().toLong
      x = (x << 7) + (b & 0x7f)
    } while ((b & 0x80) != 0L)
    x
  }

  /** Read a long number in signed big endian format, base 256. */
  def readLong(len: Int): Long = {
    var x = 0L
    var i = 0
    while (i < len) {
      x = (x << 8) + (readByte() & 0xff)
      i += 1
    }
    val leading = 64 - (len << 3)
    x << leading >> leading
  }

  def readUtf8(length: Int): String = {
    val savedIndex = readIndex
    readIndex += length
    new String(bytes, savedIndex, length, StandardCharsets.UTF_8)
  }

  def until[T](end: Int, op: () => T): List[T] = {
    if (readIndex >= end) List()
    else {
      val startIdx = readIndex
      val result = op()

      if (startIdx == readIndex) List()
      else result :: until(end, op)
    }
  }

  /** Pickle = majorVersion_Nat minorVersion_Nat nbEntries_Nat {Entry}
    *  Entry  = type_Nat length_Nat [actual entries]
    *
    *  Assumes that the ..Version_Nat are already consumed.
    *
    *  @return an array mapping entry numbers to locations in
    *  the byte array where the entries start.
    */
  def createIndex(): Array[Int] = {
    val size = readNat()
    val index = new Array[Int](size) // nbEntries_Nat
    var i = 0
    while (i < size) {
      index(i) = readIndex
      readByte() // skip type_Nat
      readIndex = readNat() + readIndex // read length_Nat, jump to next entry
      i += 1
    }
    index
  }

  private def skipVersion(): Unit = {
    val major = readNat()
    val minor = readNat()
  }
}
