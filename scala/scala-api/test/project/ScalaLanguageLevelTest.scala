package org.jetbrains.plugins.scala.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaLanguageLevelTest {
  @ParameterizedTest
  @MethodSource(Array("arguments"))
  def findByVersion(version: String, expected: Option[ScalaLanguageLevel]): Unit = {
    val actual = ScalaLanguageLevel.findByVersion(version)
    assertEquals(expected, actual)
  }
}

object ScalaLanguageLevelTest {
  def arguments: java.util.stream.Stream[Arguments] = Seq(
    ("2.9.1", Some(ScalaLanguageLevel.Scala_2_9)),
    ("2.10.7", Some(ScalaLanguageLevel.Scala_2_10)),
    ("2.11.12", Some(ScalaLanguageLevel.Scala_2_11)),
    ("2.12.21", Some(ScalaLanguageLevel.Scala_2_12)),
    ("2.13.18", Some(ScalaLanguageLevel.Scala_2_13)),
    ("3.1.2", Some(ScalaLanguageLevel.Scala_3_1)),
    ("3.10.0-RC1", Some(ScalaLanguageLevel.Scala_3_10)),
    ("3.3.8-RC2", Some(ScalaLanguageLevel.Scala_3_3)),
    ("3.33.3-RC3", None),
    ("2.1.3", None),
    ("3.10alpha", None),
    ("3.100.1", None),
    ("3.9.1-cafebabe", Some(ScalaLanguageLevel.Scala_3_9)),
    ("3.9/2", None),
    ("3.9-SNAPSHOT", Some(ScalaLanguageLevel.Scala_3_9))
  ).map {
    case (version, expected) => Arguments.of(version, expected)
  }.asJava.stream()
}
