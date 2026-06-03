package org.jetbrains.plugins.scala.conversion.copy

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.assertEquals

class BindingCellRendererTest extends ScalaLightCodeInsightFixtureTestCase {

  //noinspection ApiStatus,UnstableApiUsage
  private def assertImportStatementText(
    binding: Associations.BindingLike,
    features: ScalaFeatures,
    expectedText: String,
  ): Unit = {
    val actualText = BindingCellRenderer.buildImportStatementText(binding, features)
    assertEquals(expectedText, actualText)
  }

  def testRenderImport(): Unit = {
    assertImportStatementText(
      Associations.MockBinding("org.example.MyClass"),
      ScalaFeatures.forParserTests(ScalaVersion.Latest.Scala_2_13),
      "import org.example.MyClass",
    )
  }

  def testRenderImport_Renamed(): Unit = {
    assertImportStatementText(
      Associations.MockBinding("org.example.MyClass", Some("MyClassRenamed")),
      ScalaFeatures.forParserTests(ScalaVersion.Latest.Scala_2_13),
      "import org.example.{MyClass => MyClassRenamed}",
    )
  }

  def testRenderImport_Renamed_Scala3(): Unit = {
    assertImportStatementText(
      Associations.MockBinding("org.example.MyClass", Some("MyClassRenamed")),
      ScalaFeatures.forParserTests(ScalaVersion.Latest.Scala_3),
      "import org.example.MyClass as MyClassRenamed",
    )
  }
}