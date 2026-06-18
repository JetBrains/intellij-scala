package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.codeInsight.{InlayHintsTestBase, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.ScalaVersion

class InterleavedClausesInlayParameterHintsTest extends InlayHintsTestBase {

  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  def testParameterHintsInInterleavedValueClauses(): Unit = {
    val settings = ScalaCodeInsightSettings.getInstance
    val oldShowParameterNames = settings.showParameterNames
    try {
      settings.showParameterNames = true
      doInlayTest(
        s"""def foo[T](foo: T)[U](bar: U): Unit = ()
           |
           |foo(${S}foo = ${E}1)(${S}bar = ${E}"value")
           |""".stripMargin
      )
    } finally {
      settings.showParameterNames = oldShowParameterNames
    }
  }
}
