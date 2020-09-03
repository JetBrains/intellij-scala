package org.jetbrains.sbt

import junit.framework.TestCase

class JvmMemorySizeTest extends TestCase {
  import org.junit.Assert._

  def testFactories(): Unit = {
    def testFactory(factory: JvmMemorySize.Factory, size: Long): Unit = {
      assertEquals(size, factory.byteMultiplier)
      val memorySize = factory.apply(2)
      assertEquals(size * 2L, memorySize.sizeInBytes)
    }

    import JvmMemorySize._
    testFactory(Bytes, 1L)
    testFactory(Kilobytes, 1024L)
    testFactory(Megabytes, 1024L * 1024L)
    testFactory(Gigabytes, 1024L * 1024L * 1024L)
    testFactory(Terabytes, 1024L * 1024L * 1024L * 1024L)
  }

  def testParse(): Unit = {
    def assertMemorySizeString(str: String, size: JvmMemorySize): Unit = JvmMemorySize.parse(str) match {
      case Some(pSize) =>
        assertEquals(size, pSize)
        assertEquals(str, pSize.toString)
        assertEquals(size.toString, pSize.toString.toUpperCase)
      case None =>
        ???
    }

    import JvmMemorySize._
    assertMemorySizeString("10", Bytes(10))
    assertMemorySizeString("1000", Bytes(1000))

    assertMemorySizeString("10k", Kilobytes(10))
    assertMemorySizeString("1000K", Kilobytes(1000))
    assertMemorySizeString("99999999999999999K", Kilobytes(99999999999999999L))

    assertMemorySizeString("10m", Megabytes(10))
    assertMemorySizeString("1000M", Megabytes(1000))

    assertMemorySizeString("10g", Gigabytes(10))
    assertMemorySizeString("1000G", Gigabytes(1000))

    assertMemorySizeString("10t", Terabytes(10))
    assertMemorySizeString("1000T", Terabytes(1000))

    for {
      unit <- JvmMemorySize.units
      size <- Seq(0, 1, 10, 1024)
    } {
      val sizeInByte = unit.byteMultiplier * size
      val testString = size.toString + unit.unitSuffix

      val parsedOpt = JvmMemorySize.parse(testString)
      assert(parsedOpt.isDefined)
      val parsed = parsedOpt.get
      assertEquals(sizeInByte, parsed.sizeInBytes)
      assertEquals(testString, parsed.toString)
    }
  }

  def testEquals(): Unit = {
    import JvmMemorySize._

    assertEquals(Kilobytes(1), Bytes(1024))
    assertNotEquals(Kilobytes(2), Bytes(2025))
    assertEquals(Megabytes(1), Kilobytes(1024))
    assertEquals(Gigabytes(1), Megabytes(1024))
    assertEquals(Terabytes(1), Gigabytes(1024))
  }

  def testComparable(): Unit = {
    import JvmMemorySize._

    assert(Kilobytes(1) < Bytes(1025))
    assert(Kilobytes(1) > Bytes(1023))
    assert(Kilobytes(1) >= Bytes(1024))
    assert(Kilobytes(1) <= Bytes(1024))

    assert(Kilobytes(1025) > Megabytes(1))

    assert(Gigabytes(2) < Megabytes(2049))
  }
}
