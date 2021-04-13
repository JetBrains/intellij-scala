package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Roman.Shein
  * @since 01.04.2016.
  */
class SetConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL4941(): Unit = checkTextHasNoErrors(
    s"""
       |def f(collect: Iterable[Int]): Unit = {
       |  collect.zipWithIndex.foldLeft(mutable.LinkedHashMap.empty[Int, Set[Int]]) {
       |    case (m, (t1, _)) => m += (t1 -> {
       |      val s = m.getOrElse(t1, mutable.LinkedHashSet.empty)
       |      s
       |    })
       |  }
       |}
       |//true
    """.stripMargin)

  def testSCL11139(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.reflect.Manifest
       |object App {
       |  def tryCast[T](o: Any)(implicit manifest: Manifest[T]): Option[T] = {
       |    val clazz = manifest.runtimeClass.asInstanceOf[Class[T]]
       |    if (clazz.isAssignableFrom(o.getClass)) {
       |      Some(o.asInstanceOf[T])
       |    } else {
       |      None
       |    }
       |  }
       |
       |  def main(arg: Array[String]) = {
       |    val text: String = Seq("a", 1)
       |      .flatMap(tryCast[String])
       |      .mkString
       |    println(text)
       |  }
       |}
       |//true
    """.stripMargin)

  //component(3) = "thing" line makes the test fail with some exception from test framework, it has too many errors
  def testSCL13432(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.reflect.ClassTag
       |import scala.collection.mutable
       |
       |def component[T: ClassTag]: mutable.HashMap[Int, T] = ???
       |
       |component.update(3, "thing")
       |//component(3) = "thing"
       |
       |//true
    """.stripMargin)

  def testSCL9738(): Unit = {
    checkTextHasNoErrors(
      s"""
         |sealed trait FeedbackReason
         |case object CostReason extends FeedbackReason
         |case object BugsReason extends FeedbackReason
         |case object OtherReason extends FeedbackReason
         |
         |object FeedbackTypes {
         |  def asMap(): Map[FeedbackReason, String] = {
         |    val reasons = Map(
         |      CostReason -> "It's too expensive",
         |      BugsReason -> "It's buggy"
         |    )
         |    reasons ++ Map(OtherReason -> "Some other reason")
         |  }
         |}
      """.stripMargin)
  }
}
