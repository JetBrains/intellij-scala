package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ShapelessTest extends TypeInferenceTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  implicit private def moduleContext: Module = module()

  override protected def afterSetUpProject(): Unit = {
    super.afterSetUpProject()
    DependencyManager().load("com.chuusai" % "shapeless_2.11" % "2.3.2")
  }

  def testGeneric(): Unit = doTest(
    s"""
       |import shapeless._
       |case class Person(name: String, age: Int)
       |val Miles = Generic[Person].to(Person("Miles", 32))
       |${START}Miles.head$END
       |//String
    """.stripMargin

  )

  def testLabelledGeneric(): Unit = doTest(
    s"""
      |import shapeless.LabelledGeneric
      |import shapeless.record._
      |case class Person(name: String, age: Int)
      |val Miles = LabelledGeneric[Person].to(Person("Miles", 32))
      |${START}Miles('age)$END
      |//Int
    """.stripMargin
  )
}
