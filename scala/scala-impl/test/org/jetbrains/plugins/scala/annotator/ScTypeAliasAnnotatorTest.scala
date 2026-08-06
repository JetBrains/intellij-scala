package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

class ScTypeAliasAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testOpaqueTypeWithoutRightHandSide(): Unit = assertMessages(
    "opaque type T",
    Error("opaque", "Opaque type must have a right-hand side")
  )
}
