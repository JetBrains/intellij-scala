package org.jetbrains.plugins.scala.lang.macros

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ShapelessTest extends TypeInferenceTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    IvyManagedLoader("com.chuusai" %% "shapeless" % "2.3.3") :: Nil

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

  def testProductArgs(): Unit = doTest(
    s"""
       |import shapeless._
       |
       |object Foo extends ProductArgs {
       |  def applyProduct[L](args: L): L = args
       |  def listProduct[L](args: L): List[L] = List(args)
       |}
       |
       |val apply = Foo(1)
       |val apply2 = Foo.apply(1)
       |val list = Foo.list(1)
       |
       |$START(apply, apply2, list)$END
       |//(Int :: HNil, Int :: HNil, List[Int :: HNil])
     """.stripMargin
  )

  def testProductArgsStringInterpolator(): Unit = doTest(
    s"""
       |import shapeless._
       |
       |trait Bar[T]
       |implicit val barString: Bar[String] = ???
       |
       |implicit class barInterpolator(val sc: StringContext) {
       |  object bar extends ProductArgs {
       |    def applyProduct[T, A <: HList](a: A)(implicit ev: Bar[T]): T :: A = ???
       |  }
       |}
       |val x = 1
       |${START}bar"$$x"$END
       |//String :: Int :: HNil
     """.stripMargin
  )
}
