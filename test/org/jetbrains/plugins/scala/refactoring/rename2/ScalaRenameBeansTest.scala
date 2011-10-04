package org.jetbrains.plugins.scala.refactoring.rename2

/**
 * User: Alefas
 * Date: 04.10.11
 */

class ScalaRenameBeansTest extends ScalaRenameTestBase {
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
}