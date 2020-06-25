package org.jetbrains.plugins.scala.codeInspection.feature

import com.intellij.testFramework.EditorTestUtil

class ReflectiveCallsLanguageFeatureTest extends LanguageFeatureInspectionTestBase  {
  override protected val classOfInspection = classOf[LanguageFeatureInspection]
  override protected val description = "Advanced language feature: reflective call"

  def test_duck(): Unit = checkTextHasError(
    s"""
      |def quacker(duck: {def quack(value: String): String; def walk(): String}): Unit = {
      |  println (duck.${START}quack${END}("Quack"))
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
