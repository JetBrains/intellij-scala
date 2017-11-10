package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader.{Bundles, IvyType}
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoaderAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11, Scala_2_12}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ShapelessTest extends TypeInferenceTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  case class ShapelessLoader()(implicit val module: Module) extends IvyLibraryLoaderAdapter {
    override val name: String = "shapeless"
    override val vendor: String = "com.chuusai"
    override val version: String = "2.3.2"
    override val ivyType: IvyType = Bundles
  }

  override protected def additionalLibraries() = Seq(ShapelessLoader()(getModuleAdapter))

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
