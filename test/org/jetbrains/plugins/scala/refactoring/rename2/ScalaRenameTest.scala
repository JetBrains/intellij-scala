package org.jetbrains.plugins.scala.refactoring.rename2

/**
 * User: Alefas
 * Date: 04.10.11
 */

class ScalaRenameTest extends ScalaRenameTestBase {
  def testRenameBeanProperty() {
    val fileText =
      """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  val x<caret> = 1
      |
      |  getX()
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("y")

    val resultText =
      """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  val y<caret> = 1
      |
      |  getY()
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameBooleanBeanProperty() {
    val fileText =
      """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  val x<caret> = 1
      |
      |  isX()
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("y")

    val resultText =
      """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  val y<caret> = 1
      |
      |  isY()
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameBeanVarProperty() {
    val fileText =
      """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  var x<caret> = 1
      |
      |  getX()
      |  setX(2)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("y")

    val resultText =
      """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  var y<caret> = 1
      |
      |  getY()
      |  setY(2)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameBooleanBeanVarProperty() {
    val fileText =
      """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  var x<caret> = 1
      |
      |  isX()
      |  setX(2)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("y")

    val resultText =
      """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  var y<caret> = 1
      |
      |  isY()
      |  setY(2)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }
  
  def testRenameNamingParameter() {
    val fileText =
      """
      |class Check {
      |  def method(<caret>attrib: String) = {
      |     CaseClass(attrib = attrib)
      |  }
      |}
      |case class CaseClass(attrib: String) {}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("y")

    val resultText =
      """
      |class Check {
      |  def method(<caret>y: String) = {
      |     CaseClass(attrib = y)
      |  }
      |}
      |case class CaseClass(attrib: String) {}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameCaseClass() {
    val fileText =
      """
      |class A {
      |  case class Index()
      |
      |  Index() match {
      |    case Index() =>
      |  }
      |}
      |
      |class B {
      |  case class Index()
      |
      |  Index() match {
      |    case <caret>Index() =>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("Inde")

    val resultText =
      """
      |class A {
      |  case class Index()
      |
      |  Index() match {
      |    case Index() =>
      |  }
      |}
      |
      |class B {
      |  case class Inde()
      |
      |  Inde() match {
      |    case <caret>Inde() =>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }
  
  def testRenameInterpolatedStringPrefix() {
    val fileText =
      """
        | object AAA {
        |   class BBB { def aa<caret>a(a: Any*) = a.length }
        |   
        |   implicit def ctxToB(ctx: StringContext) = new BBB
        |   
        |   val a = aaa"blah blah"
        | }
      """.replace("\r", "").stripMargin
    
    val resultText =
      """
        | object AAA {
        |   class BBB { def bb<caret>b(a: Any*) = a.length }
        |   
        |   implicit def ctxToB(ctx: StringContext) = new BBB
        |   
        |   val a = bbb"blah blah"
        | }
      """.replace("\r", "").stripMargin
    
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("bbb")
    myFixture.checkResult(resultText)
  }

  def testObjectToCaseClass() {
    val fileText =
      """
      |object ObjectToCaseClass {
      |  case class Test1(a: Int)
      |  Test1(2)
      |  <caret>Test1.apply(1)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    myFixture.renameElementAtCaret("I")

    val resultText =
      """
      |object ObjectToCaseClass {
      |  case class I(a: Int)
      |  I(2)
      |  <caret>I.apply(1)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }
}