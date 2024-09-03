package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.annotator.{Message, ScalaHighlightingTestLike}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.OptionExt

import scala.jdk.CollectionConverters.ListHasAsScala

abstract class SetConformanceTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {

  ////TODO: replace with "checkTextHasNoErrors" when SCL-4941 is fixed
  def testSCL4941(): Unit = checkErrorsText(
    s"""import scala.collection._
       |
       |def f(collect: Iterable[Int]): Unit = {
       |  collect.zipWithIndex.foldLeft(mutable.LinkedHashMap.empty[Int, Set[Int]]) {
       |    case (m, (t1, _)) => m += (t1 -> {
       |      val s = m.getOrElse(t1, mutable.LinkedHashSet.empty)
       |      s
       |    })
       |  }
       |}
       |//true
    """.stripMargin,
    """Error({
      |    case (m, (t1, _)) => m += (t1 -> {
      |      val s = m.getOrElse(t1, mutable.LinkedHashSet.empty)
      |      s
      |    })
      |  },Type mismatch, expected: (mutable.LinkedHashMap[Int, Set[Int]], (Int, Int)) => mutable.LinkedHashMap[Int, Set[Int]], actual: (mutable.LinkedHashMap[Int, Set[Int]], (Int, Int)) => Any)""".stripMargin
  )

  //TODO: replace with "checkTextHasNoErrors" when SCL-13432 is fixed
  //component(3) = "thing" line makes the test fail with some exception from test framework, it has too many errors
  def testSCL13432(): Unit = checkErrorsText(
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
    """.stripMargin,
    """Error("thing",Type mismatch, expected: Nothing, actual: String)"""
  )

  override protected def checkTextHasNoErrors(text: String): Unit = {
    checkErrorsText(text, "")
  }

  protected def checkErrorsText(text: String, expectedErrorsText: String): Unit = {
    configureScalaFromFileText(text)

    val highlightings = getFixture.doHighlighting().asScala.toSeq
    val actualFileText = getFile.getText
    val errors = highlightings.flatMap(Message.fromHighlightInfo(_, actualFileText).filterByType[Message.Error])
    assertMessagesTextImpl(
      expectedErrorsText,
      errors
    )
  }
}

