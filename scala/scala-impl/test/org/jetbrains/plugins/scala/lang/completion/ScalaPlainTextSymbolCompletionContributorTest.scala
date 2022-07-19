package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.{PlainPrefixMatcher, PlainTextSymbolCompletionContributorEP}
import com.intellij.psi.PsiFile
import org.junit.Assert.{assertEquals, assertNotNull}

class ScalaPlainTextSymbolCompletionContributorTest
  extends base.ScalaLightCodeInsightFixtureTestAdapter {

  def testTopLevelDefinitions(): Unit = {
    implicit val file: PsiFile = configureFromFileText(
      s"""sealed trait Foo {
         |  def foo: Int
         |}
         |
         |final case class Bar(foo: Int) extends Foo
         |
         |case object Baz extends Foo {
         |  override def foo = 42
         |}""".stripMargin
    )

    doAutoPopupTest("", "Foo", "Bar", "Baz")
    doAutoPopupTest("f", "Foo")
    doAutoPopupTest("B", "Bar", "Baz")

    doBasicTest("f", "Foo", "foo")
    // TODO top level functions in Scala 3
  }

  def testInnerDefinitions(): Unit = {
    implicit val file: PsiFile = configureFromFileText(
      s"""class Foo {
         |  type Alias = Int
         |
         |  val value: Alias = 42
         |  var variable: Alias = 42
         |
         |  def method(): Unit = {}
         |
         |  class Bar {
         |    private val baz = 42
         |  }
         |}""".stripMargin
    )

    doAutoPopupTest("Foo.", "Foo.Alias", "Foo.value", "Foo.variable", "Foo.method", "Foo.Bar")
    doAutoPopupTest("Foo.Bar.", "Foo.Bar.baz")
  }

  private def doAutoPopupTest(prefix: String,
                              expected: String*)
                             (implicit file: PsiFile): Unit =
    doTest(0, prefix)(expected.toSet)

  private def doBasicTest(prefix: String,
                          expected: String*)
                         (implicit file: PsiFile): Unit =
    doTest(1, prefix)(expected.toSet)

  private def doTest(invocationCount: Int,
                     prefix: String)
                    (expected: Set[String])
                    (implicit file: PsiFile): Unit = {
    val contributor = PlainTextSymbolCompletionContributorEP.forLanguage(ScalaLanguage.INSTANCE)
    assertNotNull(contributor)

    val matcher = new PlainPrefixMatcher(prefix)
    import scala.jdk.CollectionConverters._
    val actual = contributor
      .getLookupElements(file, invocationCount, prefix)
      .asScala
      .map(_.getLookupString)
      .filter(matcher.prefixMatches)
      .toSet

    assertEquals(expected, actual)
  }
}
