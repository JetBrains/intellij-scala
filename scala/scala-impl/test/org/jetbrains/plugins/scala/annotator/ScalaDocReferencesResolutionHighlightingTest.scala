package org.jetbrains.plugins.scala.annotator

/**
 * NOTE: For the more detailed resolution tests see [[org.jetbrains.plugins.scala.lang.resolve2.ScalaDocLinkResolveTest]]
 */
class ScalaDocReferencesResolutionHighlightingTest extends ScalaHighlightingTestBase {

  //SCL-15288
  def testUnresolvedReferencesShouldBeHighlightedAsWarnings(): Unit = {
    val code =
      """/**
        | * Bad references:
        | *  - [[unknown1]]
        | *  - [[unknown2.unknown3]]
        | *  - [[unknown4.unknown5.unknown6]]
        | *
        | *  - [[MyObject.unknown7]]
        | *  - [[MyObject.unknown8.unknown9]]
        | *  - [[MyObject.unknown10.unknown11.unknown12]]
        | *
        | *  - [[MyObject.MyObjectInner.unknown13]]
        | *  - [[MyObject.MyObjectInner.unknown14.unknown15]]
        | *  - [[MyObject.MyObjectInner.unknown16.unknown17.unknown18]]
        | *
        | * Ok references:
        | *  - [[MyObject]]
        | *  - [[MyObject.foo1]]
        | *  - [[MyObject.MyObjectInner]]
        | */
        |object UnresolvedRefsInScalaDoc2
        |
        |object MyObject {
        |  def foo1: String = null
        |
        |  def foo2: String = null
        |
        |  def foo2(x: Int): String = null
        |
        |  object MyObjectInner
        |}
        |""".stripMargin

    assertMessagesText(
      code,
      """
        |Warning(unknown1,Cannot resolve symbol unknown1)
        |Warning(unknown3,Cannot resolve symbol unknown3)
        |Warning(unknown6,Cannot resolve symbol unknown6)
        |
        |Warning(unknown7,Cannot resolve symbol unknown7)
        |Warning(unknown9,Cannot resolve symbol unknown9)
        |Warning(unknown12,Cannot resolve symbol unknown12)
        |
        |Warning(unknown13,Cannot resolve symbol unknown13)
        |Warning(unknown15,Cannot resolve symbol unknown15)
        |Warning(unknown18,Cannot resolve symbol unknown18)
        |""".stripMargin
    )
  }

  def testReferencesToObjects(): Unit = {
    val code =
      """/**
        | *  - [[MyClassWithoutCompanion]]
        | *  - [[MyClassWithoutCompanion$]]
        | *
        | *  - [[MyObjectWithoutCompanion]]
        | *  - [[MyObjectWithoutCompanion$]]
        | *
        | *  - [[MyClassWithCompanion]]
        | *  - [[MyClassWithCompanion$]]
        | *
        | *  - [[MyClassWithoutCompanion.fooInClass]]
        | *  - [[MyClassWithoutCompanion$.fooInClass]]
        | *
        | *  - [[MyObjectWithoutCompanion.fooInObject]]
        | *  - [[MyObjectWithoutCompanion$.fooInObject]]
        | *
        | *  - [[MyClassWithCompanion.fooInClass]]
        | *  - [[MyClassWithCompanion.fooInObject]]
        | *
        | *  - [[MyClassWithCompanion$.fooInClass]]
        | *  - [[MyClassWithCompanion$.fooInObject]]
        | */
        |object Example
        |
        |class MyClassWithoutCompanion {
        |  def fooInClass: String = null
        |}
        |
        |object MyObjectWithoutCompanion {
        |  def fooInObject: String = null
        |}
        |
        |class MyClassWithCompanion {
        |  def fooInClass: String = null
        |}
        |
        |object MyClassWithCompanion {
        |  def fooInObject: String = null
        |}
        |""".stripMargin
    assertMessagesText(
      code,
      """Warning(MyClassWithoutCompanion$,Cannot resolve symbol MyClassWithoutCompanion$)
        |Warning(fooInClass,Cannot resolve symbol fooInClass)
        |Warning(fooInClass,Cannot resolve symbol fooInClass)
        |""".stripMargin
    )
  }

  def testReferencesToObjects_FullyQualified(): Unit = {
    val code =
      """package org
        |package example
        |
        |/**
        | *  - [[org.example.MyClassWithoutCompanion]]
        | *  - [[org.example.MyClassWithoutCompanion$]]
        | *
        | *  - [[org.example.MyObjectWithoutCompanion]]
        | *  - [[org.example.MyObjectWithoutCompanion$]]
        | *
        | *  - [[org.example.MyClassWithCompanion]]
        | *  - [[org.example.MyClassWithCompanion$]]
        | *
        | *  - [[org.example.MyClassWithoutCompanion.fooInClass]]
        | *  - [[org.example.MyClassWithoutCompanion$.fooInClass]]
        | *
        | *  - [[org.example.MyObjectWithoutCompanion.fooInObject]]
        | *  - [[org.example.MyObjectWithoutCompanion$.fooInObject]]
        | *
        | *  - [[org.example.MyClassWithCompanion.fooInClass]]
        | *  - [[org.example.MyClassWithCompanion.fooInObject]]
        | *
        | *  - [[org.example.MyClassWithCompanion$.fooInClass]]
        | *  - [[org.example.MyClassWithCompanion$.fooInObject]]
        | */
        |object Example
        |
        |class MyClassWithoutCompanion {
        |  def fooInClass: String = null
        |}
        |
        |object MyObjectWithoutCompanion {
        |  def fooInObject: String = null
        |}
        |
        |class MyClassWithCompanion {
        |  def fooInClass: String = null
        |}
        |
        |object MyClassWithCompanion {
        |  def fooInObject: String = null
        |}
        |""".stripMargin
    assertMessagesText(
      code,
      """Warning(MyClassWithoutCompanion$,Cannot resolve symbol MyClassWithoutCompanion$)
        |Warning(fooInClass,Cannot resolve symbol fooInClass)
        |Warning(fooInClass,Cannot resolve symbol fooInClass)
        |""".stripMargin
    )
  }
}
