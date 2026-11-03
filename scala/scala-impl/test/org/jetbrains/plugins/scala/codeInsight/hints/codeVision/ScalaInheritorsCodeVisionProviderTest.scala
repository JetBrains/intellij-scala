package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

class ScalaInheritorsCodeVisionProviderTest extends ScalaCodeVisionTestBase {

  override protected def enabledGroupIds: Set[String] = Set("inheritors")

  def testInheritorsAndOverrides(): Unit = doTest(
    """trait Shape {/*<# [2 implementations] #>*/
      |  def area: Double/*<# [2 implementations] #>*/
      |  def name: String = "shape"/*<# [1 override] #>*/
      |}
      |
      |class Square extends Shape {
      |  override def area: Double = 1
      |  override def name: String = "square"
      |}
      |
      |class Circle extends Shape {
      |  override def area: Double = 3.14
      |}
      |
      |final class Finished
      |""".stripMargin
  )

  def testTooManyInheritors(): Unit = doTest(
    """abstract class Base/*<# [5+ inheritors] #>*/
      |class A1 extends Base
      |class A2 extends Base
      |class A3 extends Base
      |class A4 extends Base
      |class A5 extends Base
      |class A6 extends Base
      |""".stripMargin
  )

  // the class is typed first and made an inheritor afterwards, as when editing in the IDE
  def testCountIsUpdatedWhenExtendsClauseIsAdded(): Unit = {
    doTest(
      """trait Shape {/*<# [1 implementation] #>*/
        |  def area: Double/*<# [1 implementation] #>*/
        |}
        |
        |class Square extends Shape {
        |  override def area: Double = 1
        |}
        |""".stripMargin
    )

    insertAfter("override def area: Double = 1\n}\n", "\nclass Circle {\n  def area: Double = 3.14\n}\n")
    assertHints(
      """trait Shape {/*<# [1 implementation] #>*/
        |  def area: Double/*<# [1 implementation] #>*/
        |}
        |
        |class Square extends Shape {
        |  override def area: Double = 1
        |}
        |
        |class Circle {
        |  def area: Double = 3.14
        |}
        |""".stripMargin
    )

    insertAfter("class Circle", " extends Shape")
    assertHints(
      """trait Shape {/*<# [2 implementations] #>*/
        |  def area: Double/*<# [2 implementations] #>*/
        |}
        |
        |class Square extends Shape {
        |  override def area: Double = 1
        |}
        |
        |class Circle extends Shape {
        |  def area: Double = 3.14
        |}
        |""".stripMargin
    )
  }
}
