package org.jetbrains.plugins.scala.project

import org.junit.Assert._
import org.junit.Test

/**
  * @author Pavel Fatin
  */
class VersionTest {
  @Test
  def comarison(): Unit = {
    assertEquals(0, Version("1").compareTo(Version("1")))
    assertEquals(0, Version("1.2").compareTo(Version("1.2")))
    assertEquals(0, Version("1.2.3").compareTo(Version("1.2.3")))

    assertEquals(-1, Version("1").compareTo(Version("2")))
    assertEquals(1, Version("2").compareTo(Version("1")))

    assertEquals(-1, Version("1.1").compareTo(Version("1.2")))
    assertEquals(1, Version("1.2").compareTo(Version("1.1")))

    assertEquals(-1, Version("1.1").compareTo(Version("1.2")))
    assertEquals(1, Version("1.2").compareTo(Version("1.1")))

    assertEquals(-1, Version("1.2").compareTo(Version("1.2.1")))
    assertEquals(1, Version("1.2.1").compareTo(Version("1.2")))
  }

  @Test
  def comarisonGroups(): Unit = {
    assertEquals(0, Version("1-2").compareTo(Version("1-2")))

    assertEquals(-1, Version("1-1").compareTo(Version("2-1")))
    assertEquals(1, Version("2-1").compareTo(Version("1-1")))

    assertEquals(-1, Version("1").compareTo(Version("1-1")))
    assertEquals(1, Version("1-1").compareTo(Version("1")))
  }
}