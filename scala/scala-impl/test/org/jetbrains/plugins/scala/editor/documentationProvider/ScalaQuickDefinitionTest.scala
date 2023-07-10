package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.ImplementationViewComponent
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.{assertEquals, assertNotNull}

class ScalaQuickDefinitionTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  //NOTE: I didn't find a better existing way to test "quick definition" action (aka ShowImplementationsAction)
  //This test is based on `com.jetbrains.cidr.lang.documentation.OCQuickDefinitionTest` from IntelliJ code
  private def getQuickDefinitionText(code: String): String = {
    configureFromFileText(code)
    val targetElement = TargetElementUtil.findTargetElement(getEditor, TargetElementUtil.getInstance.getAllAccepted)
    assertNotNull("Target element is null", targetElement)
    ImplementationViewComponent.getNewText(targetElement)
  }

  private def doQuickDefinitionTextTest(
    code: String,
    expectedQuickDefText: String
  ): Unit = {
    val actualQuickDefText = getQuickDefinitionText(code)
    assertEquals(
      "Quick definition text mismatch",
      expectedQuickDefText,
      actualQuickDefText
    )
  }

  def testClassPrimaryConstructor(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |class Foo2(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  ${CARET}Foo2(1, 2)
         |  Foo2(1)
         |}
         |""".stripMargin,
      """class Foo2(x: Int, y: Int) {"""
    )

  def testClassAuxiliaryConstructor(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |class Foo2(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  Foo2(1, 2)
         |  ${CARET}Foo2(1)
         |}
         |""".stripMargin,
      """  /** Description 2 */
        |  def this(x: Int) = this(x, 2)""".stripMargin
    )

  def testEnum(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |enum Foo(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  /** Description of Bar 1 */
         |  case Bar1 extends Foo(1, 2)
         |
         |  /** Description of Bar 2 */
         |  case Bar2 extends Foo(1)
         |}
         |
         |val value = ${CARET}Foo.Bar1
         |""".stripMargin,
      """/** Description 1 */
        |enum Foo(x: Int, y: Int) {
        |
        |  /** Description 2 */
        |  def this(x: Int) = this(x, 2)
        |
        |  /** Description of Bar 1 */
        |  case Bar1 extends Foo(1, 2)
        |
        |  /** Description of Bar 2 */
        |  case Bar2 extends Foo(1)
        |}""".stripMargin
    )

  def testEnumAtExtendsPositionPrimaryConstructor(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |enum Foo(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  /** Description of Bar 1 */
         |  case Bar1 extends ${CARET}Foo(1, 2)
         |
         |  /** Description of Bar 2 */
         |  case Bar2 extends Foo(1)
         |}""".stripMargin,
      """enum Foo(x: Int, y: Int) {"""
    )

  def testEnumAtExtendsPositionAuxiliaryConstructor(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |enum Foo(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  /** Description of Bar 1 */
         |  case Bar1 extends Foo(1, 2)
         |
         |  /** Description of Bar 2 */
         |  case Bar2 extends ${CARET}Foo(1)
         |}""".stripMargin,

      //TODO: this is a wrong expected result.
      // When SCL-21381 is fixed this test is expected to fail.
      // In that case adjust the expected result, which should contain auxiliary constructor definition text:
      //  def this(x: Int) = this(x, 2)
      """enum Foo(x: Int, y: Int) {""".stripMargin
    )

  def testEnumCase(): Unit =
    doQuickDefinitionTextTest(
      s"""/** Description 1 */
         |enum Foo(x: Int, y: Int) {
         |
         |  /** Description 2 */
         |  def this(x: Int) = this(x, 2)
         |
         |  /** Description of Bar 1 */
         |  case Bar1 extends Foo(1, 2)
         |
         |  /** Description of Bar 2 */
         |  case Bar2 extends Foo(1)
         |}
         |
         |val value = Foo.${CARET}Bar1
         |""".stripMargin,
      """  case Bar1 extends Foo(1, 2)"""
    )
}
