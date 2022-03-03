package org.jetbrains.plugins.scala.codeInspection.unused
import org.jetbrains.plugins.scala.ScalaVersion

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class Scala3UsedLocalSymbolOneContainerInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

// Should be enabled when implementing Scala 3 unused symbol inspection and enabling
// it in ScalaUnusedSymbolInspect#shouldProcessElement
//  def testPrivateField(): Unit = {
//    val code =
//      s"""
//         |extension (s: Int)
//         |  private def ${START}blub$END: Int = 3
//      """.stripMargin
//    checkTextHasError(code)
//    val before =
//      """
//        |object Test {
//        |  extension (s: Int)
//        |    private def blub: int = 3
//        |}
//      """.stripMargin
//    val after =
//      """
//        |object Test {
//        |  extension (s: Int)
//        |}
//      """.stripMargin
//    testQuickFix(before, after, hint)
//  }

  def test_extension_method(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  extension(i: Int)
         |    private def plus0: Int = i + 0
         |  0.plus0
         |end Foo
         |""".stripMargin)

  def test_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case Banana }
         |  import Fruit.*
         |  Banana match { case Banana => }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit(@unused val i: Int) { case Banana extends Fruit(42) }
         |  import Fruit.*
         |  Banana match { case Banana => }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum_case(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case Banana(@unused i: Int) }
         |  import Fruit.*
         |  Banana(42) match { case _: Banana => }
         |end Foo
         |""".stripMargin)

  def testThatShouldFailToPreventAutoMerge(): Unit = assert(false)
}

