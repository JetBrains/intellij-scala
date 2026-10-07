package org.jetbrains.sbt.lang.completion

import junit.framework.TestCase
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.{Scala_3_7, Scala_3_8, Scala_3_9}
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.{ArgType, Deprecation}
import org.junit.Assert.assertEquals

class UpdateScalacOptionsInfoTest extends TestCase {
  def testMergePreservesDeprecationsForAllVersions(): Unit = {
    val deprecation38 = Deprecation("", None)
    val deprecation39 = Deprecation("Use -Vphases instead", Some("-Vphases"))
    val options = Seq(
      option(Scala_3_7, None),
      option(Scala_3_8, Some(deprecation38)),
      option(Scala_3_9, Some(deprecation39)),
    )

    val merged = options.reduce(UpdateScalacOptionsInfo.mergeScalacOptions)

    assertEquals(Map(Scala_3_8 -> deprecation38, Scala_3_9 -> deprecation39), merged.deprecations)
  }

  def testMergePreservesDeprecationsWhenLaterVersionIsNotDeprecated(): Unit = {
    val deprecation = Deprecation("", None)
    val merged = UpdateScalacOptionsInfo.mergeScalacOptions(
      option(Scala_3_8, Some(deprecation)),
      option(Scala_3_9, None),
    )

    assertEquals(Map(Scala_3_8 -> deprecation), merged.deprecations)
  }

  private def option(version: ScalaLanguageLevel, deprecation: Option[Deprecation]): SbtScalacOptionInfo =
    SbtScalacOptionInfo(
      flag = "-Xshow-phases",
      descriptions = Map(version -> "List compiler phases."),
      choices = Map.empty,
      argType = ArgType.No,
      scalaVersions = Set(version),
      defaultValue = None,
      deprecations = deprecation.map(version -> _).toMap,
    )
}
