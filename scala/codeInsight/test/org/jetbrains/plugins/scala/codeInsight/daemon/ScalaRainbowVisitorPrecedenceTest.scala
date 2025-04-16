package org.jetbrains.plugins.scala.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

import scala.jdk.CollectionConverters.CollectionHasAsScala


class ScalaRainbowVisitorPrecedenceTest extends ScalaLightCodeInsightFixtureTestCase {

  private def doTest(text: String): Unit = {
    myFixture.configureByText("dummy.scala", text)
    CodeInsightTestFixtureImpl.runWithRainbowEnabled(true, () => {
      val highlights = myFixture.doHighlighting().asScala.toSeq

      assert(highlights.exists(_.getSeverity != HighlightSeverity.ERROR))

      def isRainbowInfo(info: HighlightInfo): Boolean =
        Option(info.forcedTextAttributesKey).exists(_.getExternalName.startsWith("TEMP::RAINBOW_TEMP_"))

      val grouped = highlights.groupBy(i => (i.getStartOffset, i.getEndOffset))
      grouped.values.foreach { infos =>
        if (infos.exists(isRainbowInfo)) {
          // if we have a rainbow info, we want it to have the highest precedence
          val sorted = infos.sortBy(_.getSeverity.myVal)
          assert(isRainbowInfo(sorted.last), s"Expected Rainbow info to have the highest precedenece, but got:\n${sorted.mkString(",\n")}")
        }
      }
    })
  }

  def testEverything(): Unit = doTest(
    """
      |case class CaseClass(a: Int, b: Int) {
      |  val c: Int = 1
      |
      |  def foo(x: Int, y: Int): Int =
      |    a + x + b + y + c
      |}
      |
      |class NormalClass(a: Int, val b: Int) {
      |  val c: Int = 1
      |  val CaseClass(d, e) = CaseClass(a, this.b)
      |
      |  def foo(x: Int, y: Int): Int =
      |    a + x + b + y + c
      |}
      |
      |object Test {
      |  def local = {
      |    val a = 1
      |    println(a)
      |
      |    val b@_ = 1
      |    println(b)
      |
      |    val CaseClass(c, d) = CaseClass(1, 2)
      |    println(c + d)
      |
      |    Seq(1, 2).foreach { num =>
      |      println(num)
      |    }
      |
      |    for (x <- Seq(1, 2); y = x) {
      |      println(x + y)
      |    }
      |  }
      |}
      |""".stripMargin
  )
}
