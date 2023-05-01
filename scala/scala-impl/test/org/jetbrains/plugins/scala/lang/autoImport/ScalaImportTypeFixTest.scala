package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.runner.RunWith

abstract class ScalaImportTypeFixTestBase extends ImportElementFixTestBase[ScReference] {
  override def createFix(ref: ScReference): Option[ScalaImportTypeFix] =
    Option(ref).map(ScalaImportTypeFix(_))
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0,
))
@RunWith(classOf[MultipleScalaVersionsRunner])
class ScalaImportTypeFixTest extends ScalaImportTypeFixTestBase {
  def testCaseClass(): Unit = {
    val fileText =
      s"""object Source {
         |  case class Foo
         |}
         |
         |object Target {
         |  val foo = ${CARET}Foo()
         |}
         |""".stripMargin
    val qNameToImport = "Source.Foo"

    checkElementsToImport(
      fileText,
      qNameToImport
    )

    doTest(
      fileText,
      expectedText = s"import $qNameToImport\n\n${fileText.replace(CARET, "")}",
      selected = qNameToImport
    )
  }
}

class Scala3ImportTypeFixTest extends ScalaImportTypeFixTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testClass(): Unit = {
    val fileText =
      s"""object Source:
         |  class Foo
         |
         |object Target:
         |  val foo = ${CARET}Foo()
         |""".stripMargin
    val qNameToImport = "Source.Foo"

    checkElementsToImport(
      fileText,
      qNameToImport
    )

    doTest(
      fileText,
      expectedText = s"import $qNameToImport\n\n${fileText.replace(CARET, "")}",
      selected = qNameToImport
    )
  }

  def testClassInsideGivenImport(): Unit = doTest(
    fileText =
      s"""
         |object Test:
         |  import Givens.given F${CARET}oo
         |
         |object Givens:
         |  class Foo
         |  given Foo = Foo()
         |""".stripMargin,
    expectedText =
      """
        |import Givens.Foo
        |
        |object Test:
        |  import Givens.given Foo
        |
        |object Givens:
        |  class Foo
        |  given Foo = Foo()
        |""".stripMargin,

    "Givens.Foo"
  )

  def testEnum_WithoutCompanion(): Unit = {
    val fileText =
      s"""@main def main(): Unit:
         |  My${CARET}FoodEnum
         |
         |object OtherScope:
         |  enum MyFoodEnum:
         |    case Cheese, Pepperoni, Mushrooms
         |""".stripMargin

    checkElementsToImport(
      fileText,
      "OtherScope.MyFoodEnum"
    )

    doTest(
      fileText,
      expectedText =
        """import OtherScope.MyFoodEnum
          |
          |@main def main(): Unit:
          |  MyFoodEnum
          |
          |object OtherScope:
          |  enum MyFoodEnum:
          |    case Cheese, Pepperoni, Mushrooms
          |""".stripMargin,
      "OtherScope.MyFoodEnum"
    )
  }

  def testEnum_WithCompanion(): Unit = {
    val fileText =
      s"""@main def main(): Unit:
         |  My${CARET}FoodEnum
         |
         |object OtherScope:
         |  enum MyFoodEnum:
         |    case Cheese, Pepperoni, Mushrooms
         |  enum MyFoodEnum
         |""".stripMargin

    checkElementsToImport(
      fileText,
      "OtherScope.MyFoodEnum",
      "OtherScope.MyFoodEnum", // companion object
    )

    doTest(
      fileText,
      expectedText =
        """import OtherScope.MyFoodEnum
          |
          |@main def main(): Unit:
          |  MyFoodEnum
          |
          |object OtherScope:
          |  enum MyFoodEnum:
          |    case Cheese, Pepperoni, Mushrooms
          |  enum MyFoodEnum
          |""".stripMargin,
      "OtherScope.MyFoodEnum"
    )
  }

  def testEnum_WithCaseAfter_WithoutCompanion(): Unit = doTest(
    fileText =
      s"""@main def main(): Unit:
         |  My${CARET}FoodEnum.Pepperoni
         |
         |object OtherScope:
         |  enum MyFoodEnum:
         |    case Cheese, Pepperoni, Mushrooms
         |""".stripMargin,
    expectedText =
      """import OtherScope.MyFoodEnum
        |
        |@main def main(): Unit:
        |  MyFoodEnum.Pepperoni
        |
        |object OtherScope:
        |  enum MyFoodEnum:
        |    case Cheese, Pepperoni, Mushrooms
        |""".stripMargin,

    "OtherScope.MyFoodEnum"
  )

  def testEnum_WithCaseAfter_WithCompanion(): Unit = doTest(
    fileText =
      s"""@main def main(): Unit:
         |  My${CARET}FoodEnum.Pepperoni
         |
         |object OtherScope:
         |  enum MyFoodEnum:
         |    case Cheese, Pepperoni, Mushrooms
         |  enum MyFoodEnum
         |""".stripMargin,
    expectedText =
      """import OtherScope.MyFoodEnum
        |
        |@main def main(): Unit:
        |  MyFoodEnum.Pepperoni
        |
        |object OtherScope:
        |  enum MyFoodEnum:
        |    case Cheese, Pepperoni, Mushrooms
        |  enum MyFoodEnum
        |""".stripMargin,

    "OtherScope.MyFoodEnum"
  )
}
