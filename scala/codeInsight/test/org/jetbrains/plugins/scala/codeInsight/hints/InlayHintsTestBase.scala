package org.jetbrains.plugins.scala
package codeInsight
package hints

abstract class InlayHintsTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  override protected final def configureFromFileText(fileText: String) =
    super.configureFromFileText(
      s"""class Foo {
         |$fileText
         |}
         |
         |new Foo""".stripMargin
    )
}

private[hints] object InlayHintsTestBase {

  val HintStart = "<hint text=\""
  val HintEnd = "\"/>"
}