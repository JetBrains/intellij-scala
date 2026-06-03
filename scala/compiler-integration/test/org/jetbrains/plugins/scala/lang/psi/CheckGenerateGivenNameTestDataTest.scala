package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.{CheckTestDataTestBase, LatestScalaVersions}

// checks if the test data in GenerateGivenNameTest.allTests is correct
class CheckGenerateGivenNameTestDataTest extends CheckTestDataTestBase(GenerateGivenNameTest.testData, LatestScalaVersions.Scala_3_6) {
  override def buildCompleteSucceedingTestCode(): String =
    s"""
       |import language.experimental.namedTuples
       |
       |${super.buildCompleteSucceedingTestCode()}
       |""".stripMargin
}
