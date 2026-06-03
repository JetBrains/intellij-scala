package org.jetbrains.plugins.scala.codeInspection.feature

class ReflectiveCallsLanguageFeatureTest extends LanguageFeatureInspectionTestBase {
  override protected val description = "Advanced language feature: reflective call"

  def test_duck(): Unit = {
    val before =
      s"""def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println (duck.${START}quack${END}("Quack"))
         |}
         |""".stripMargin
    val after =
      s"""import scala.language.reflectiveCalls
         |
         |def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
         |  println (duck.quack("Quack"))
         |}
         |""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, hint = "Import feature flag for reflective calls")
  }

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
