package org.jetbrains.plugins.scala.codeInspection.collections

/**
 * @author Nikolay.Tropin
 */
class IfElseToOptionTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[IfElseToOptionInspection]

  override def hint: String = "Replace with Option(x)"

  def test1(): Unit = {
    doTest(
      s"val x = 0; ${START}if (x == null) None else Some(x)$END",
      "val x = 0; if (x == null) None else Some(x)",
      "val x = 0; Option(x)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"val x = 0; ${START}if (x != null) Some(x) else None$END",
      "val x = 0; if (x != null) Some(x) else None",
      "val x = 0; Option(x)"
    )
  }

  def test3(): Unit = {
    doTest(
      s"""val x = 0
         |${START}if (x == null) {
         |  None
         |}
         |else {
         |  Some(x)
         |}$END""".stripMargin,
      """val x = 0
        |if (x == null) {
        |  None
        |}
        |else {
        |  Some(x)
        |}""".stripMargin,
      """val x = 0
        |Option(x)""".stripMargin
    )
  }

  def test4(): Unit = {
    doTest(
      s"val x = 0; ${START}if (null == x) None else Some(x)$END",
      "val x = 0; if (null == x) None else Some(x)",
      "val x = 0; Option(x)"
    )
  }
}
