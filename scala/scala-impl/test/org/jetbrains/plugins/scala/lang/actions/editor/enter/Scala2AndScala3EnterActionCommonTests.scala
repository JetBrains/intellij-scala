package org.jetbrains.plugins.scala.lang.actions.editor.enter

import junit.framework.Test
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.withModifiedSetting
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

trait Scala2AndScala3EnterActionCommonTests extends Test {
  self: EditorActionTestBase =>

  def testNoIndentAfterReturn_UnitType(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""def foo: Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""def foo: scala.Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: scala.Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""def foo: _root_.scala.Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: _root_.scala.Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
  }

  def testNoIndentAfterReturnWithExpression(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""def foo: Int = {
         |  if (true)
         |    return 1$CARET
         |
         |  2
         |}""".stripMargin,
      s"""def foo: Int = {
         |  if (true)
         |    return 1
         |    $CARET
         |
         |  2
         |}""".stripMargin
    )
  }

  def testIndentAfterReturn_NonUnitType(): Unit = checkGeneratedTextAfterEnter(
    s"""def foo: Int = {
       |  return$CARET
       |}""".stripMargin,
    s"""def foo: Int = {
       |  return
       |    $CARET
       |}""".stripMargin
  )

  def testIndentAfterReturn_NoType(): Unit = checkGeneratedTextAfterEnter(
    s"""def foo = {
       |  return$CARET
       |}""".stripMargin,
    s"""def foo = {
       |  return
       |    $CARET
       |}""".stripMargin
  )

  def testInExtendsList_AfterExtends(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""abstract class B extends A$CARET
         |  with C""".stripMargin,
      s"""abstract class B extends A
         |  $CARET
         |  with C""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""abstract class B
         |  extends A$CARET
         |    with C""".stripMargin,
      s"""abstract class B
         |  extends A
         |    $CARET
         |    with C""".stripMargin
    )
  }

  def testInExtendsList_AfterMiddleWith(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""class A extends B
         |  with C1$CARET
         |  with C2""".stripMargin,
      s"""class A extends B
         |  with C1
         |  $CARET
         |  with C2""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""class A
         |  extends B
         |    with C1$CARET
         |    with C2""".stripMargin,
      s"""class A
         |  extends B
         |    with C1
         |    $CARET
         |    with C2""".stripMargin
    )
  }

  def testInExtendsList_AfterLastWith(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""abstract class B extends A
         |  with C$CARET
         |""".stripMargin,
      s"""abstract class B extends A
         |  with C
         |  $CARET
         |""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""abstract class B extends A
         |  with G1[G2[G3[G4]]]$CARET
         |""".stripMargin,
      s"""abstract class B extends A
         |  with G1[G2[G3[G4]]]
         |  $CARET
         |""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""abstract class B extends A
         |  with C$CARET
         |
         |class DummyClass
         |""".stripMargin,
      s"""abstract class B extends A
         |  with C
         |  $CARET
         |
         |class DummyClass
         |""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""class A
         |  extends B
         |    with C$CARET""".stripMargin,
      s"""class A
         |  extends B
         |    with C
         |    $CARET""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""class A
         |  extends B
         |    with C$CARET {
         |
         |}""".stripMargin,
      s"""class A
         |  extends B
         |    with C
         |    $CARET {
         |
         |}""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""class A
         |    extends B
         |    with C1
         |    with C2 $CARET{
         |
         |}""".stripMargin,
      s"""class A
         |    extends B
         |    with C1
         |    with C2 ${""}
         |    $CARET{
         |
         |}""".stripMargin
    )
  }

  def testInExtendsList_AfterExtendsAndWith_AlignExtendsAndWithSettingEnabled(): Unit = {
    withModifiedSetting(getScalaSettings)(ScalaCodeStyleSettings.ALIGN_TO_EXTENDS)(_.ALIGN_EXTENDS_WITH, _.ALIGN_EXTENDS_WITH = _) {
      checkGeneratedTextAfterEnter(
        s"""abstract class A
           |  extends B$CARET
           |  with C
           |""".stripMargin,
        s"""abstract class A
           |  extends B
           |  $CARET
           |  with C
           |""".stripMargin
      )

      checkGeneratedTextAfterEnter(
        s"""abstract class A
           |  extends B
           |  with C1$CARET
           |  with C2
           |""".stripMargin,
        s"""abstract class A
           |  extends B
           |  with C1
           |  $CARET
           |  with C2
           |""".stripMargin
      )

      checkGeneratedTextAfterEnter(
        s"""abstract class A
           |  extends B
           |  with C1
           |  with C2$CARET
           |""".stripMargin,
        s"""abstract class A
           |  extends B
           |  with C1
           |  with C2
           |  $CARET
           |""".stripMargin
      )
    }
  }
}
