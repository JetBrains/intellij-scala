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

  def testClassInAnnotationPosition(): Unit = checkNoImportFix(
    s"""object Source {
       |  class Foo
       |}
       |
       |@${CARET}Foo
       |object Target
       |""".stripMargin
  )

  def testCaseClassInAnnotationPosition(): Unit = checkNoImportFix(
    s"""object Source {
       |  case class Foo
       |}
       |
       |@${CARET}Foo
       |object Target
       |""".stripMargin
  )

  def testTraitInAnnotationPosition(): Unit = checkNoImportFix(
    s"""object Source {
       |  trait Foo
       |}
       |
       |@${CARET}Foo
       |object Target
       |""".stripMargin
  )

  def testObjectInAnnotationPosition(): Unit = checkNoImportFix(
    s"""object Source {
       |  object Foo
       |}
       |
       |@${CARET}Foo
       |object Target
       |""".stripMargin
  )

  def testScalaAnnotationClassInAnnotationPosition(): Unit = {
    val fileText =
      s"""object Source {
         |  class Foo extends scala.annotation.StaticAnnotation
         |}
         |
         |@${CARET}Foo
         |object Target
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

  def testJavaAnnotationInAnnotationPosition(): Unit = {
    val fileText =
      s"""@${CARET}Baz
         |object Target
         |""".stripMargin
    val qNameToImport = "Bar.Baz"

    myFixture.addFileToProject(
      "Bar.java",
      """import java.lang.annotation.Retention;
        |import java.lang.annotation.RetentionPolicy;
        |import java.lang.annotation.Target;
        |
        |import static java.lang.annotation.ElementType.*;
        |
        |public class Bar {
        |    @Retention(RetentionPolicy.RUNTIME)
        |    @Target(value={TYPE})
        |    public static @interface Baz {}
        |}
        |""".stripMargin
    )

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

  def testInsideExport(): Unit = doTest(
    fileText =
      s"""
         |object Module:
         |  object Blub:
         |    def foo = 42
         |
         |object Test:
         |  export ${CARET}Blub.foo
         |""".stripMargin,
    """
      |import Module.Blub
      |
      |object Module:
      |  object Blub:
      |    def foo = 42
      |
      |object Test:
      |  export Blub.foo
      |""".stripMargin,
    "Module.Blub"
  )
}
