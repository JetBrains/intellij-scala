package org.jetbrains.plugins.scala.project

import java.io.{ByteArrayOutputStream, ObjectOutputStream}

import org.jetbrains.plugins.scala.extensions
import org.junit.Assert._
import org.junit.Test

/**
  * @author Pavel Fatin
  */
class VersionTest {
  @Test
  def parsing(): Unit = {
    assertEquals("", Version("").toString)

    assertEquals("0", Version("0").toString)
    assertEquals("0.0", Version("0.0").toString)

    assertEquals("1", Version("1").toString)
    assertEquals("1.0", Version("1.0").toString)
    assertEquals("1.2", Version("1.2").toString)
    assertEquals("1.2.3", Version("1.2.3").toString)

    assertEquals("1-0", Version("1-0").toString)
    assertEquals("1-2", Version("1-2").toString)
    assertEquals("1-2-3", Version("1-2-3").toString)

    assertEquals("1.2.3-4.5.6", Version("1.2.3-4.5.6").toString)

    assertEquals("1.2", Version("1M2").toString)

    assertEquals("20100817020148", Version("20100817020148").toString)
  }

  @Test
  def comparison(): Unit = {
    assertEquals(0, Version("0").compareTo(Version("0")))

    assertEquals(0, Version("1").compareTo(Version("1.0")))
    assertEquals(0, Version("1.0").compareTo(Version("1")))

    assertEquals(0, Version("1").compareTo(Version("1")))
    assertEquals(0, Version("1.1").compareTo(Version("1.1")))
    assertEquals(0, Version("1.1.1").compareTo(Version("1.1.1")))

    assertEquals(-1, Version("1").compareTo(Version("2")))
    assertEquals(1, Version("2").compareTo(Version("1")))

    assertEquals(-1, Version("1.1").compareTo(Version("2.1")))
    assertEquals(1, Version("2.1").compareTo(Version("1.1")))

    assertEquals(-1, Version("1.1").compareTo(Version("1.2")))
    assertEquals(1, Version("1.2").compareTo(Version("1.1")))

    assertEquals(-1, Version("1").compareTo(Version("1.1")))
    assertEquals(1, Version("1.1").compareTo(Version("1")))
  }

  @Test
  def comparisonGroups(): Unit = {
    assertEquals(0, Version("1").compareTo(Version("1-0")))
    assertEquals(0, Version("1-0").compareTo(Version("1")))

    assertEquals(0, Version("1-1").compareTo(Version("1-1")))

    assertEquals(-1, Version("1-1").compareTo(Version("2-1")))
    assertEquals(1, Version("2-1").compareTo(Version("1-1")))

    assertEquals(-1, Version("1-1").compareTo(Version("1-2")))
    assertEquals(1, Version("1-2").compareTo(Version("1-1")))

    assertEquals(-1, Version("1").compareTo(Version("1-1")))
    assertEquals(1, Version("1-1").compareTo(Version("1")))
  }

  @Test
  def equivalence(): Unit = {
    assertTrue(Version("1") ~= Version("1.0"))
    assertTrue(Version("1.0") ~= Version("1"))

    assertTrue(Version("1") ~= Version("1"))
    assertTrue(Version("1.1") ~= Version("1.1"))
    assertTrue(Version("1.1.1") ~= Version("1.1.1"))

    assertFalse(Version("1") ~= Version("2"))
    assertFalse(Version("2") ~= Version("1"))

    assertFalse(Version("1.1") ~= Version("2.1"))
    assertFalse(Version("2.1") ~= Version("1.1"))

    assertFalse(Version("1.1") ~= Version("1.2"))
    assertFalse(Version("1.2") ~= Version("1.1"))

    assertFalse(Version("1") ~= Version("1.1"))
    assertTrue(Version("1.1") ~= Version("1"))
  }

  @Test
  def equivalenceGroups(): Unit = {
    assertTrue(Version("1") ~= Version("1-0"))
    assertTrue(Version("1-0") ~= Version("1"))

    assertTrue(Version("1-1") ~= Version("1-1"))

    assertFalse(Version("1-1") ~= Version("2-1"))
    assertFalse(Version("2-1") ~= Version("1-1"))

    assertFalse(Version("1-1") ~= Version("1-2"))
    assertFalse(Version("1-2") ~= Version("1-1"))

    assertFalse(Version("1") ~= Version("1-1"))
    assertTrue(Version("1-1") ~= Version("1"))
  }

  @Test
  def majorVersions(): Unit = {
    assertTrue(Version("1.2.3").major(0) == Version(""))
    assertTrue(Version("1.2.3").major(1) == Version("1"))
    assertTrue(Version("1.2.3").major(10) == Version("1.2.3"))
    assertTrue(Version("1.2.3.4-RC2").major(2) == Version("1.2"))
    assertTrue(Version("1.0").major(2) == Version("1.0"))
    assertTrue(Version("1").major(2) == Version("1"))
    assertTrue(Version("").major(2) == Version(""))
  }

  @Test
  def serialization(): Unit = {
    extensions.using(new ObjectOutputStream(new ByteArrayOutputStream(1024)))(
      _.writeObject(Version("1.2.3")))
  }
}