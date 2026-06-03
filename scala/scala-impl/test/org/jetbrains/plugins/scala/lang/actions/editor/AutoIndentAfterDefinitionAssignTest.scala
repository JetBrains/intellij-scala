package org.jetbrains.plugins.scala.lang.actions.editor

class AutoIndentAfterDefinitionAssignTest extends EditorTypeActionTestBase {

  override protected def typedChar: Char = '1'

  def testIndent(): Unit =
    doTest(
      s"""def foo =
         |$CARET""".stripMargin,
      s"""def foo =
         |  1$CARET""".stripMargin
    )

  def testIndent_WithTypeAnnotation(): Unit =
    doTest(
      s"""def foo: Int =
         |$CARET""".stripMargin,
      s"""def foo: Int =
         |  1$CARET""".stripMargin
    )

  def testIndent_Nested(): Unit =
    doTest(
      s"""class A {
         |  def foo =
         |$CARET
         |}""".stripMargin,
      s"""class A {
         |  def foo =
         |    1$CARET
         |}""".stripMargin
    )
}
