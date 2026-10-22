package org.jetbrains.plugins.scala.lang.scaladoc.editor.enter

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.documentationProvider.JavaDocTemplateTestUtils

class Scala3DocStubGenerationEnterTest extends ScalaDocEnterTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def setUp(): Unit = {
    super.setUp()
    JavaDocTemplateTestUtils.enableJavaDocMethodTemplate(getProject, getTestRootDisposable)
  }

  def testMethodParamAndReturnStub(): Unit =
    doTest(
      s"""object Test {
         |  /**${|}
         |  def test(i: Int, s: String): Int = ???
         |}""".stripMargin,
      s"""object Test {
         |  /**
         |   * ${|}
         |   * @param i
         |   * @param s
         |   * @return
         |   */
         |  def test(i: Int, s: String): Int = ???
         |}""".stripMargin
    )

  def testClassParamAndTypeParamStub(): Unit =
    doTest(
      s"""/**${|}
         |class Test[A](a: A)""".stripMargin,
      s"""/**
         | * ${|}
         | * @param a
         | * @tparam A
         | */
         |class Test[A](a: A)""".stripMargin
    )
}
