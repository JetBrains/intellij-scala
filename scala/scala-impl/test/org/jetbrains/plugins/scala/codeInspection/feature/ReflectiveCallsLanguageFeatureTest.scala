package org.jetbrains.plugins.scala.codeInspection.feature

import org.jetbrains.plugins.scala.ScalaVersion

abstract class ReflectiveCallsLanguageFeatureTestBase extends LanguageFeatureInspectionTestBase {
  override protected val description = "Advanced language feature: reflective call"

  override def descriptionMatches(s: String): Boolean =
    s == description || s == "Unused import statement"

//  override def setUp(): Unit = {
//    super.setUp()
//    myFixture.enableInspections(classOf[UnusedImportInspection])
//  }

  def doQuickfixTest(before: String, after: String): Unit = {
    checkTextHasError(before)
    testQuickFix(before, after, hint = "Import feature flag for reflective calls")
    checkTextHasNoErrors(after)
  }
}

class ReflectiveCallsLanguageFeatureTest_2 extends ReflectiveCallsLanguageFeatureTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala2

  def test_duck(): Unit = doQuickfixTest(
    before =
      s"""def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println(duck.${START}quack${END}("Quack"))
         |}
         |""".stripMargin,
    after =
      s"""import scala.language.reflectiveCalls
         |
         |def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println(duck.quack("Quack"))
         |}
         |""".stripMargin
  )

  def test_SCL15905(): Unit = checkTextHasNoErrors(
    """
      |def LTT[T]: Int = 3
      |
      |type X = {type A = Int}
      |val a1: X = ???
      |assert(LTT[a1.A] == LTT[X#A])
      |""".stripMargin
  )
}

class ReflectiveCallsLanguageFeatureTest_3 extends ReflectiveCallsLanguageFeatureTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def test_duck(): Unit = doQuickfixTest(
    before =
      s"""def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println(duck.${START}quack${END}("Quack"))
         |}
         |""".stripMargin,
    after =
      s"""import scala.reflect.Selectable.reflectiveSelectable
         |
         |def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println(duck.quack("Quack"))
         |}
         |""".stripMargin
  )
}