package org.jetbrains.plugins.scala.annotator

class ScOverriddenVarAnnotatorTest extends ScalaHighlightingTestBase {
  def testValueParameterOverridesTraitAbstractVariable(): Unit = {
    val scalaCode =
      """
        |trait Animal { var cat: String }
        |
        |class Cat(override val cat: String) extends Animal
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    println(errors.head.message)
    assert(errors.exists(err => err.element == "cat" && err.message == "Mutable variable cannot be overridden"))
  }

  def testValueOverridesTraitAbstractVariable(): Unit = {
    val scalaCode =
      """
        |trait Animal { var cat: String }
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Missing implementation for the setter: cat"))
  }

  def testValueOverridesVariableParameter(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String = "")
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Mutable variable cannot be overridden"))
  }

  def testValueParameterOverridesVariableParameter(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String)
        |
        |class Cat(override val cat: String) extends Animal(cat)
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Mutable variable cannot be overridden"))
  }

  def testVariableOverridesVariableParameter(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String)
        |
        |class Cat extends Animal("") {
        |  override var cat: String = "cat"
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Mutable variable cannot be overridden"))
  }

  def testMethodOverridesVariableParameter(): Unit = {
    val scalaCode =
      """
        |class Animal(var cat: String)
        |
        |class Cat extends Animal("") {
        |  override def cat: String = "cat"
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Mutable variable cannot be overridden"))
  }

  def testValueOverridesAbstractClassVariable(): Unit = {
    val scalaCode =
      """
        |abstract class Animal {
        |  var cat: String
        |}
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat" && err.message == "Missing implementation for the setter: cat"))
  }

  def testSetterOverridesAbstractClassVariable(): Unit = {
    val scalaCode =
      """
        |abstract class Animal {
        |  var cat: String
        |}
        |
        |class Cat extends Animal {
        |  override def cat_=(x: String): Unit = {}
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.exists(err => err.element == "cat_=" && err.message == "Missing implementation for the getter: cat"))
  }

  def testOverrideAbstractVarByVarIsOk(): Unit = {
    val scalaCode =
      """
        |trait Animal {
        |  var cat: String
        |}
        |
        |class Cat extends Animal {
        |  override var cat: String = ""
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.isEmpty)
  }

  def testOverrideVarByValRequiresSetter(): Unit = {
    val scalaCode =
      """
        |trait Animal {
        |  var cat: String
        |}
        |
        |class Cat extends Animal {
        |  val cat: String = ""
        |  def cat_=(x: String): Unit = {}
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.isEmpty)
  }

  def testOverrideVarByValRequiresSetter_1(): Unit = {
    val scalaCode =
      """
        |trait Animal {
        |  var cat: String
        |}
        |
        |class Cat extends Animal {
        |  override val cat: String = ""
        |  override def cat_=(x: String): Unit = {}
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.isEmpty)
  }

  def testOverrideModifierNotAllowedAtTopLevelDefinitions(): Unit = {
    assertErrorsText(
      """override def foo1: String = ???
        |override val foo2: String = ???
        |override var foo3: String = ???
        |""".stripMargin,
      """Error(override,'override' modifier is not allowed here)
        |Error(override,'override' modifier is not allowed here)
        |Error(override,'override' modifier is not allowed here)
        |""".stripMargin
    )
  }

  // https://youtrack.jetbrains.com/issue/SCL-20634/annotator-false-positive-Mutable-variable-cannot-be-overriden
  def testWhenGetterAndSetterAreImplemented(): Unit = {
    val scalaCode =
      """
        |abstract class Base {
        |  var value: Option[Boolean]
        |}
        |
        |class Child extends Base {
        |  override def value: Option[Boolean] = ???
        |  override def value_=(b: Option[Boolean]): Unit = ???
        |}
        |""".stripMargin

    val errors = errorsFromScalaCode(scalaCode)
    assert(errors.isEmpty)
  }
}
