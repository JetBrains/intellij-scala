package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ShapelessTest extends TypeInferenceTestBase {

  implicit private def moduleContext: Module = module()
  override implicit val version: ScalaVersion = Scala_2_11

  override protected def loadIvyDependencies(): Unit =
    DependencyManager("com.chuusai" %% "shapeless" % "2.3.2").loadAll

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

  def testTupleSyntax(): Unit = doTest(
    s"""
      |import shapeless.syntax.std.tuple._
      |val x = (1,2) :+ 1
      |${START}x$END
      |//(Int, Int, Int)
    """.stripMargin
  )
}
