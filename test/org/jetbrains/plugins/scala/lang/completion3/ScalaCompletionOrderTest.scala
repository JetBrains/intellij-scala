package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.junit.Assert

/**
 * @author Alefas
 * @since 23.03.12
 */

class ScalaCompletionOrderTest extends ScalaCodeInsightTestBase {
  def testInImportSelector() {
    val fileText =
      """
      |class B {
      |  def foo2 = 2
      |}
      |class A extends B {
      |  def foo3 = 1
      |}
      |class C {
      |  def foo1 = 3
      |}
      |implicit def a2c(a: A): C = new C
      |val a: A
      |a.foo<caret>
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    Assert.assertArrayEquals(Array[AnyRef]("foo3", "foo2", "foo1"), activeLookup.map(_.getLookupString).toArray[AnyRef])
  }
}
