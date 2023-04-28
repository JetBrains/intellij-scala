package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class PatternsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL4989(): Unit = {
    doTest(
      s"""
        |val x: Product2[Int, Int] = (10, 11)
        |val (y, _) = x
        |${START}y$END
        |//Int
      """.stripMargin,
      failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = false
    )
  }

  def testSCL6383(): Unit = {
    doTest(
      s"""
         |object Test {
         |  class R[T]
         |  case object MyR extends R[Int]
         |  def buggy[T] : PartialFunction[R[T], T] = { case MyR => ${START}3$END }
         |}
         |//T
      """.stripMargin)
  }

  def testSCL8323(): Unit = {
    doTest(
      s"""
         |import scala.collection.Searching
         |import scala.collection.Searching.{Found, InsertionPoint}
         |
         |object CaseInsensitiveOrdering extends scala.math.Ordering[String] {
         |  def compare(a:String, b:String) = a.compareToIgnoreCase(b)
         |  def findClosest(s: String, availableNames: List[String]): String = {
         |    val sorted: List[String] = availableNames.sorted(CaseInsensitiveOrdering)
         |    Searching.search(sorted).search(s)(${START}CaseInsensitiveOrdering$END) match {
         |      case Found(_) => s
         |      case InsertionPoint(index) => sorted(index min sorted.size - 1)
         |    }
         |  }
         |}
         |//Ordering[Any]
      """.stripMargin)
  }

  def testSCL9094(): Unit = doTest()
}
