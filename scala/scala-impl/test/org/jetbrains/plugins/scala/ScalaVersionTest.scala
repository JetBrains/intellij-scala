package org.jetbrains.plugins.scala

import junit.framework.TestCase
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Version}
import org.junit.Assert._

/**
 * See also [[org.jetbrains.plugins.scala.project.VersionTest]]
 */
class ScalaVersionTest extends TestCase {

  def testParseFromString(): Unit = {
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_2_9, "0")), ScalaVersion.fromString("2.9.0"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_2_9, "3")), ScalaVersion.fromString("2.9.3"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_2_12, "10")), ScalaVersion.fromString("2.12.10"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "2")), ScalaVersion.fromString("2.13.2"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "3-RC1")), ScalaVersion.fromString("2.13.3-RC1"))

    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-M1")), ScalaVersion.fromString("3.0.0-M1"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-M2")), ScalaVersion.fromString("3.0.0-M2"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "1-M1")), ScalaVersion.fromString("3.0.1-M1"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "1-M2")), ScalaVersion.fromString("3.0.1-M2"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-RC1")), ScalaVersion.fromString("3.0.0-RC1"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-RC2")), ScalaVersion.fromString("3.0.0-RC2"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-RC10")), ScalaVersion.fromString("3.0.0-RC10"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0")), ScalaVersion.fromString("3.0.0"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "1")), ScalaVersion.fromString("3.0.1"))

    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_1, "0")), ScalaVersion.fromString("3.1.0"))
    assertEquals(Some(new ScalaVersion(ScalaLanguageLevel.Scala_3_1, "1")), ScalaVersion.fromString("3.1.1"))

    assertEquals(None, ScalaVersion.fromString("A.BC.3"))
    assertEquals(None, ScalaVersion.fromString("2.BC.3"))
    assertEquals(None, ScalaVersion.fromString("A.13.3"))
  }

  def testComparison(): Unit = {
    assertTrue(ScalaVersion.fromString("2.13.13").get < ScalaVersion.fromString("2.13.14").get)
    assertTrue(ScalaVersion.fromString("2.13.13").get > ScalaVersion.fromString("2.13.12").get)

    assertTrue(ScalaVersion.fromString("2.13.13").get == ScalaVersion.fromString("2.13.13").get)
    assertTrue(ScalaVersion.fromString("2.13.14-RC1").get > ScalaVersion.fromString("2.13.13").get)
    assertTrue(ScalaVersion.fromString("2.13.13-RC1").get < ScalaVersion.fromString("2.13.13").get)
    assertTrue(ScalaVersion.fromString("2.13.13-bin-db-2-fd41f6b").get > ScalaVersion.fromString("2.13.13").get)

  }
}