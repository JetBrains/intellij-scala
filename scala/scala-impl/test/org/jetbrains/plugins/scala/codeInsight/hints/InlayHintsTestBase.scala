package org.jetbrains.plugins.scala
package codeInsight
package hints

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

abstract class InlayHintsTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  protected def doTest(text: String, setOption: Boolean => Unit = _ => ()): Unit = {
    setOption(true)
    try {
      configureFromFileText(
        s"""class Foo {
           |$text
           |}
           |
           |new Foo""".stripMargin
      )
      getFixture.testInlays()
    } finally {
      setOption(false)
    }
  }
}

object InlayHintsTestBase {

  val HintStart = "<hint text=\""
  val HintEnd = "\"/>"
}
