package org.jetbrains.plugins.scala.annotator.element

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.ScalaVersion

class ScStringLiteralAnnotatorTest_Scala3 extends TestCase

object ScStringLiteralAnnotatorTest_Scala3 {
  final def suite: Test = new ScStringLiteralAnnotatorTestBase("/annotator/string_literals/scala3") {
    override def supportedInScalaVersion(version: ScalaVersion): Boolean = version.isScala3
  }
}
