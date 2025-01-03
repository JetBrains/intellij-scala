package org.jetbrains.plugins.scala.lang.navigation

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAliasDefinition, ScGivenDefinition}

class GotoGivenTest extends GotoDeclarationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  // ========================== Aliases ==============================

  def testGotoNamedGivenAlias(): Unit = doTest(
    s"""object Test {
       |  given int: Int = 0
       |  ${CARET}int
       |}
      """.stripMargin,
    expected = (is[ScGivenAliasDefinition], "int")
  )

  def testGotoNamedAnonymousGivenAlias(): Unit = doTest(
    s"""object Test {
       |  given Int = 0
       |  ${CARET}given_Int
       |}
      """.stripMargin,
    expected = (is[ScSimpleTypeElement], "SimpleType: Int")
  )

  def testGotoNamedGivenAliasWithParams(): Unit = doTest(
    s"""object Test {
       |  given int(using Int): Int = 0
       |  ${CARET}int
       |}
      """.stripMargin,
    expected = (is[ScGivenAliasDefinition], "int")
  )

  // ========================== Definitions ==============================

  def testGotoNamedGivenDefinition(): Unit = doTest(
    s"""trait A
       |object Test {
       |  given a: A with
       |    def foo: Int = 0
       |  ${CARET}a
       |}
      """.stripMargin,
    expected = (is[ScGivenDefinition], "Test.a")
  )

  def testGotoNamedAnonymousGivenDefinition(): Unit = doTest(
    s"""trait A
       |object Test {
       |  given A with
       |    def foo: Int = 0
       |  ${CARET}given_A
       |}
      """.stripMargin,
    expected = (is[ScExtendsBlock], "ExtendsBlock")
  )

  def testGotoNamedGivenDefinitionWithParams(): Unit = doTest(
    s"""trait A
       |object Test {
       |  given a(using Int): A with
       |    def foo: Int = 0
       |  ${CARET}a
       |}
      """.stripMargin,
    expected = (is[ScGivenDefinition], "Test.a")
  )
}
