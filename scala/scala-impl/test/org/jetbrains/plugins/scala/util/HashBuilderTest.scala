package org.jetbrains.plugins.scala.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.util.Objects

class HashBuilderTest extends TestCase  {

  def testHashBuilder(): Unit = {
    val a = "a"
    val b = "b"
    val c = "c"

    assertEqual(a: HashBuilder, Objects.hash(a))
    assertEqual(a #+ b, Objects.hash(a, b))
    assertEqual(a #+ a, Objects.hash(a, a))
    assertEqual(a #+ b #+ c, Objects.hash(a, b, c))
    assertEqual(c #+ b #+ a, Objects.hash(c, b, a))
    assertEqual(a #+ null #+ b, Objects.hash(a, null, b))
    assertEqual(1 #+ 2 #+ 3, Objects.hash(1: Integer, 2: Integer, 3: Integer))

    assertEqual(1 #+ 2L #+ true,
      Objects.hash(1: Integer, java.lang.Long.valueOf(2L), java.lang.Boolean.TRUE))
  }

  private def assertEqual(builder: HashBuilder, value: Int): Unit =
    assert((builder: Int) == value)
}
