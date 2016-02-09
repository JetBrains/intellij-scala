package org.jetbrains.plugins.scala.lang.completion3

/**
  * Created by kate
  * on 1/29/16
  */
class ScalaCaseClassParamCompletion extends ScalaTestCompletionWithOrder {
  def testAllSuggestion(): Unit = {
    val fileText =
      """
        |abstract case class BigCase()
        |case class GoodSportyStudent(aname: String, asurName: List[String], aimark: Int, sporta: Seq[Int]) extends BigCase
        |
        |def test(b: BigCase): Unit = {
        | b match {
        |   case GoodSportyStudent(<caret>
        | }
        |}
      """

    checkResultWithOrder(Array[AnyRef]("aname", "asurName", "aimark", "sporta"), fileText)
  }

  def testWithType(): Unit = {
    val fileText =
      """
        |abstract case class BigCase()
        |case class GoodSportyStudent(aname: String, asurName: List[String], aimark: Int, sporta: Seq[Int]) extends BigCase
        |
        |def test(b: BigCase): Unit = {
        | b match {
        |   case GoodSportyStudent(aname, asurName, aimark, s<caret>
        | }
        |}
      """

    checkResultWithOrder(Array[AnyRef]("sporta", "seq", "ints", "asurName"), fileText)
  }
}
