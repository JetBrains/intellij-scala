package org.jetbrains.plugins.scala.annotator

class ScVarOverrideAnnotatorTest extends ScalaHighlightingTestBase {
  def testOverrideParameter(): Unit = {
    val scalaCode =
      """
        |trait Animal { var cat: String }
        |
        |class Cat(override val cat: String) extends Animal
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "override val cat: String"))
  }

  def testOverrideVal(): Unit = {
    val scalaCode =
      """
        |trait Animal { var cat: String }
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat"))
  }

  def testOverrideParamByVal(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String = "")
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat"))
  }

  def testOverrideParamByParam(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String)
        |
        |class Cat(override val cat: String) extends Animal(cat)
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "override val cat: String"))
  }
}
