package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.{CheckTestDataTestBase, LatestScalaVersions}

// checks if the test data in GenerateGivenNameTest.allTests is correct
class CheckGenerateGivenNameTestDataTest extends CheckTestDataTestBase(GenerateGivenNameTest, LatestScalaVersions.Scala_3)