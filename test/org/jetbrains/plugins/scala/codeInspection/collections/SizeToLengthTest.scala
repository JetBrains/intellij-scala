package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class SizeToLengthTest extends OperationsOnCollectionInspectionTest{
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[SizeToLengthInspection]

  override def hint: String = InspectionBundle.message("size.to.length")

  def testString(): Unit = {
    doTest(s"""|"".${START}size$END""".stripMargin, "\"\".size", "\"\".length")

    doTest(
      s"""
         |object Foo {
         |  val s = ""
         |  s.${START}size$END
         |}
       """.stripMargin,
      """
         |object Foo {
         |  val s = ""
         |  s.size
         |}
       """.stripMargin,
      """
        |object Foo {
        |  val s = ""
        |  s.length
        |}
      """.stripMargin
    )
  }

  def testArray(): Unit = {
    doTest(s"Array(1, 2).${START}size$END", "Array(1, 2).size", "Array(1, 2).length")

    doTest(s"Seq(1, 2).toArray.${START}size$END", "Seq(1, 2).toArray.size", "Seq(1, 2).toArray.length")

    doTest(
      s"""
         |object Foo {
         |  val arr = Array(1, 2)
         |  arr.${START}size$END
         |}
       """.stripMargin,
      """
        |object Foo {
        |  val arr = Array(1, 2)
        |  arr.size
        |}
      """.stripMargin,
      """
        |object Foo {
        |  val arr = Array(1, 2)
        |  arr.length
        |}
      """.stripMargin
    )
  }
}
