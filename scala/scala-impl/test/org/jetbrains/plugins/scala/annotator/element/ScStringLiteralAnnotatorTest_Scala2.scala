package org.jetbrains.plugins.scala.annotator.element

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.ScalaVersion

class ScStringLiteralAnnotatorTest_Scala2 extends TestCase

object ScStringLiteralAnnotatorTest_Scala2 {
  final def suite: Test = new ScStringLiteralAnnotatorTestBase("/annotator/string_literals/scala2_after_2_13_14") {
    override def supportedInScalaVersion(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
  }
}
