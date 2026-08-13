package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

/**
 * The members backing `scala.Dynamic` are invoked with the selected member's name at the call site,
 * so their own name never appears there.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-20304]]
 */
class Scala2DynamicMemberInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  private def addUsageFile(text: String): Unit = myFixture.addFileToProject("Usage.scala", text)

  def test_select_dynamic(): Unit = {
    addUsageFile("object Usage { new Foo().fizz }")
    checkTextHasNoErrors(
      """import scala.language.dynamics
        |class Foo extends Dynamic {
        |  def selectDynamic(name: String): Any = name
        |}""".stripMargin
    )
  }

  def test_apply_dynamic(): Unit = {
    addUsageFile("object Usage { new Foo().fizz(42) }")
    checkTextHasNoErrors(
      """import scala.language.dynamics
        |class Foo extends Dynamic {
        |  def applyDynamic(name: String)(args: Any*): Any = (name, args)
        |}""".stripMargin
    )
  }

  def test_apply_dynamic_named(): Unit = {
    addUsageFile("object Usage { new Foo().fizz(bar = 42) }")
    checkTextHasNoErrors(
      """import scala.language.dynamics
        |class Foo extends Dynamic {
        |  def applyDynamicNamed(name: String)(args: (String, Any)*): Any = (name, args)
        |}""".stripMargin
    )
  }

  def test_update_dynamic(): Unit = {
    addUsageFile("object Usage { new Foo().fizz = 42 }")
    checkTextHasNoErrors(
      """import scala.language.dynamics
        |class Foo extends Dynamic {
        |  def selectDynamic(name: String): Any = name
        |  def updateDynamic(name: String)(value: Any): Unit = println((name, value))
        |}""".stripMargin
    )
  }
}
