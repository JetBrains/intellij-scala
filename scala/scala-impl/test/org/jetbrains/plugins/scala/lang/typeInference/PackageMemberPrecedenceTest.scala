package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class PackageMemberPrecedenceTest extends TypeInferenceTestBase {
  def testWildcardImportShadowsPackageMember(): Unit = doTest(
    s"""package scala.collection.mutable
       |import scala.collection.immutable._
       |object Main {
       |  val set = ${START}HashSet.empty[Int]$END
       |}
       |//HashSet[Int]
       |""".stripMargin
  )
}

class PackageMemberPrecedenceTest_Scala2_12 extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
  override protected def folderPath: Path = super.folderPath / "bugs5"

  def testSCL5475(): Unit = doTest()
}
