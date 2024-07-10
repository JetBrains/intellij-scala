package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

class ScReferenceAnnotatorTest extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  private val MyObjectFileText =
    """package other
      |
      |class MyObject
      |object MyObject {
      |  object MyInner {
      |    def foo: Int = ???
      |  }
      |}
      |""".stripMargin

  def testDontHighlightAllUnresolvedPartsOfTheQualifier_ReferenceExpression(): Unit = {
    myFixture.addFileToProject("defs.scala", MyObjectFileText)
    assertErrorsTextScala3(
      """println(MyObject.unresolved1.unresolved2)
        |
        |{
        |  import other.MyObject
        |  println(MyObject.unresolved1.unresolved2)
        |}
        |{
        |  import other.MyObject
        |  println(MyObject.MyInner.unresolved3)
        |}
        |""".stripMargin,
      """Error(MyObject,Cannot resolve symbol MyObject)
        |Error(unresolved1,Cannot resolve symbol unresolved1)
        |Error(unresolved3,Cannot resolve symbol unresolved3)
        |""".stripMargin
    )
  }

  def testDontHighlightAllUnresolvedPartsOfTheQualifier_ImportStatement(): Unit = {
    myFixture.addFileToProject("defs.scala", MyObjectFileText)
    assertErrorsTextScala3(
      """import MyObject.unresolved1.unresolved2
        |
        |{
        |  import other.MyObject
        |  import MyObject.unresolved1.unresolved2
        |}
        |
        |{
        |  import other.MyObject
        |  import MyObject.MyInner.unresolved3
        |}
        |""".stripMargin,
      """Error(MyObject,Cannot resolve symbol MyObject)
        |Error(unresolved1,Cannot resolve symbol unresolved1)
        |Error(unresolved3,Cannot resolve symbol unresolved3)
        |""".stripMargin
    )
  }

  def testDontHighlightAllUnresolvedPartsOfTheQualifier_ExportStatement(): Unit = {
    myFixture.addFileToProject("defs.scala", MyObjectFileText)
    assertErrorsTextScala3(
      """import MyObject.unresolved1.unresolved2
        |
        |{
        |  import other.MyObject
        |  import MyObject.unresolved1.unresolved2
        |}
        |
        |{
        |  import other.MyObject
        |  import MyObject.MyInner.unresolved3
        |}
        |""".stripMargin,
      """Error(MyObject,Cannot resolve symbol MyObject)
        |Error(unresolved1,Cannot resolve symbol unresolved1)
        |Error(unresolved3,Cannot resolve symbol unresolved3)
        |""".stripMargin
    )
  }
}