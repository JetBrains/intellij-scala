package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class SetConformanceTest_3 extends SetConformanceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

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
    """.stripMargin
  )

  ////TODO: replace with "checkTextHasNoErrors" when SCL-9738 is fixed
  def testSCL9738(): Unit = {
    checkErrorsText(
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
      """.stripMargin,
      """Error(},Expression of type Map[FeedbackReason & Product & Serializable, String] doesn't conform to expected type Map[FeedbackReason, String])"""
    )
  }
}
