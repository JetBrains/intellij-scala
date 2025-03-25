package org.jetbrains.plugins.scala.annotator.element

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.ScalaVersion

class ScStringLiteralAnnotatorTest_Scala2_Before_2_13_14 extends TestCase

object ScStringLiteralAnnotatorTest_Scala2_Before_2_13_14 {
  final def suite: Test = new ScStringLiteralAnnotatorTestBase("/annotator/string_literals/scala2_before_2_13_14") {
    override def supportedInScalaVersion(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13.withMinor(13)
  }
}
