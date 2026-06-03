package org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.ReferenceComparisonTestConfig.{testOutputDir, testDataBasePath}
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.{Path, Paths}

abstract class ReferenceComparisonTestConfig(
  val testClassName: String,
  val testDataPathFolder: String,
  val scalaTargetVersion: ScalaVersion,
) {
  lazy val testClassPath: Path = testOutputDir / s"$testClassName.scala"

  lazy val sourcePath: Path = testDataBasePath / testDataPathFolder / "source"
  lazy val outPath: Path = testDataBasePath / testDataPathFolder / "out"

  lazy val selfQualifiedName: String = getClass.getName.replace("$", "")
}

object ReferenceComparisonTestConfig {
  private def testDataBasePath: Path =
    Paths.get(TestUtils.getTestDataPath, "lang", "resolveSemanticDb")

  private lazy val testOutputDir: Path =
    Paths.get(TestUtils.findCommunityRoot)
      .resolve("scala/scala-impl/test/org/jetbrains/plugins/scala/lang/resolveSemanticDb/generated")

  lazy val all: Seq[ReferenceComparisonTestConfig] = Seq(
    ReferenceComparisonTestConfig_Scala3_LTS,
    ReferenceComparisonTestConfig_Scala3_Newest
  )
}