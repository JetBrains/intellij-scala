package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by kate on 6/7/16.
  */
class ApplyTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL10253(): Unit = {
    val code =
      """
        |import PersonObject.Person
        |package object PersonObject {
        |
        |  case class Person(name: String, age: Int)
        |
        |  object Person {
        |    def apply() = new Person("<no name>", 0)
        |  }
        |
        |}
        |
        |class CaseClassTest {
        |  val b = Person("William Shatner", 82)
        |}""".stripMargin

    checkTextHasNoErrors(code)
  }
}
