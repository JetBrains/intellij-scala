package org.jetbrains.plugins.scala
package lang
package formatter
package tests
package scala3

import com.intellij.lang.Language

class Scala3FormatterTest extends AbstractScalaFormatterTestBase {
  override protected def language: Language = Scala3Language.INSTANCE

  def testColon_AfterTypeDefinition(): Unit = doTextTest(
    """trait Trait:
      |  def test = ()
      |
      |class Class :
      |  def test = ()
      |
      |object Object  :
      |  def test = ()
      |
      |enum Enum   :
      |  case A
      |  case B""".stripMargin,
    """trait Trait:
      |  def test = ()
      |
      |class Class:
      |  def test = ()
      |
      |object Object:
      |  def test = ()
      |
      |enum Enum:
      |  case A
      |  case B""".stripMargin
  )

  def testColon_AfterTypeDefinition_WithParam(): Unit = doTextTest(
    """trait Trait(param: String):
      |  def test = ()
      |
      |class Class(param: String) :
      |  def test = ()
      |
      |enum Enum(param: String)   :
      |  case A
      |  case B""".stripMargin,
    """trait Trait(param: String):
      |  def test = ()
      |
      |class Class(param: String):
      |  def test = ()
      |
      |enum Enum(param: String):
      |  case A
      |  case B""".stripMargin
  )

  // NOTE: space before colon after extends list is not a strict requirement yet!
  def testColon_AfterTypeDefinition_WithExtends(): Unit = doTextTest(
    """trait Test extends Object:
      |  def test = ()
      |
      |class Test extends Object :
      |  def test = ()
      |
      |object Test extends Object  :
      |  def test = ()
      |
      |enum Enum extends Object   :
      |  case A
      |  case B
      |""".stripMargin,
    """trait Test extends Object :
      |  def test = ()
      |
      |class Test extends Object :
      |  def test = ()
      |
      |object Test extends Object :
      |  def test = ()
      |
      |enum Enum extends Object :
      |  case A
      |  case B
      |""".stripMargin
  )

  def testClassEnd(): Unit = doTextTest(
    """
      |class Test:
      |  def test = ()
      |end Test
      |""".stripMargin
  )

  //SCL-18678
  def testClassEnd_1(): Unit = doTextTest(
    """def foo(n: Int): Int =
      |  def f(x: Int) = x + 1
      |
      |  f(n)
      |end foo
      |""".stripMargin
  )

  def testEnum(): Unit = doTextTest(
    """enum MyEnum {
      |  case A
      |  case B
      |}
      |""".stripMargin
  )

  def testEnum_WithModifiersAndAnnotations(): Unit = doTextTest(
    """enum MyEnum {
      |  protected case A
      |  final case B // NOTE: only access modifiers are supported but we shouldn't fail anyway
      |  @deprecated case B
      |  @deprecated
      |  case C
      |  @deprecated
      |  protected case C
      |}
      |""".stripMargin
  )

  def testEnum_WithModifiersAndAnnotations_InMembers(): Unit = doTextTest(
    """enum MyEnum {
      |  final val a = 0
      |  lazy val b = 0
      |  protected val c = 0
      |  private final val d = 0
      |
      |  @deprecated
      |  private final val e = 0
      |
      |  @deprecated
      |  private def f1 = 0
      |
      |  protected final def f2 = 0
      |
      |  final def f3 = 0
      |
      |  final type X = String
      |
      |  protected object Inner
      |
      |  case A
      |  case B
      |}
      |""".stripMargin
  )
}
