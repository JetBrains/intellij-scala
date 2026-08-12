package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.GeneratedHighlightingParameterizedTest
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.{SimpleTestData, testDataFromCode}

object WideningTest {
  private lazy val testData: Seq[String] = Seq(
    """
      |// WideningOnVal_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val x = <1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>
      |val y: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = x // <Error in both, Error in both, Error in both, Error in both, Error in [Scala3], fine>
      |""".stripMargin.multi,
    """
      |// WideningOnTypedVal_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = <1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>
      |val y = x
      |val z: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = y // <Error in both, Error in both, Error in [Scala3], Error in both, fine, fine>
      |""".stripMargin.multi,
    """
      |// WideningThroughFunc_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |def test[T](t: T): T = t
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = test(<1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>)
      |""".stripMargin.multi,
    """
      |// WideningIntoContainer_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// SCL-23271
      |case class Test[T](t: T) {
      |  def foo(t: T): Unit = ()
      |}
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val y = Test(<1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>)
      |y.foo(<2, "y", c: Any, E.B, new Object, new reflect.Selectable {}>) // <fine, fine, fine, fine, Error in [Scala2], Error in [Scala3]>
      |""".stripMargin.multi,
    """
      |// WideningIntoContainer2_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |case class Test[T](t: T) {
      |  def foo(t: T): Unit = ()
      |}
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |val y = Test(x)
      |y.foo(<2, "y", c: Any, E.B, new Object, new reflect.Selectable {}>) // <fine, fine, fine, fine, Error, Error>
      |""".stripMargin.multi,
    """
      |// WideningOnContainerTypeInference_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |case class Test[T](t: T) {
      |  def map[TT](t: T => TT): Test[TT] = ???
      |}
      |def identity[T](input: T): T = input
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |val x = Test[<1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }>](???).map(identity(_))
      |val y: Test[<1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }>] = x // <Error, Error, Error, Error, fine, fine>
      |""".stripMargin.multi,
    """// NonWideningOfFinalFields_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |object Test {
      |  final val x = <1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>
      |}
      |
      |val y: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = Test.x // <fine, fine, Error, Error, Error in [Scala3], fine>
      |""".stripMargin.multi,
    """// NonWideningOfFinalDefs_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |object Test {
      |  final def x = <1, "x", c, E.A, new { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>
      |}
      |
      |val y: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = Test.x // <Error in both, Error in both, Error in both, Error in both, Error in [Scala3], fine>
      |""".stripMargin.multi,
    """// WidenOnImplicit_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |enum E { case A, B }  [Scala3]
      |trait C // we need C, otherwise the implicit search will be underspecified
      |val c: C = ???
      |
      |class Test[Upper] {
      |  def test[X <: Upper](implicit x: X): X = x
      |}
      |
      |val inst = new Test[<Int, String, C, E, C, reflect.Selectable>]
      |implicit val x: <1, "x", c.type, E.A.type, C { def bar: Int }, reflect.Selectable { def bar: Int }> = <1, "x", c, E.A, new C { def bar: Int = 1 }, new reflect.Selectable { def bar: Int = 1 }>
      |
      |val y = inst.test
      |val z: <1, "x", c.type, E.A.type, C { def bar: Int }, reflect.Selectable { def bar: Int }> = x
      |""".stripMargin.multi
  ).flatten

  lazy val testDataInScala2: Seq[SimpleTestData] =
    testData.map(toTestData("[Scala3]")).filterNot(_.testName.contains("Enum")).filterNot(_.testName.contains("Selectable"))
  lazy val testDataInScala3: Seq[SimpleTestData] =
    testData.map(toTestData("[Scala2]"))

  private implicit class StringExt(private val string: String) {
    def single: Seq[String] = Seq(string)
    def multi: Seq[String] = {
      val pattern = "<([^<>]+)>".r
      val infos = pattern.findAllMatchIn(string).map { m =>
        val options = m.group(1).split(',').map(_.trim).toSeq
        (m.group(0), options)
      }.toSeq

      for (i <- 0 until infos.map(_._2.length).max)
        yield infos.foldLeft(string) {
          case (acc, (full, options)) => acc.replace(full, options(i))
        }
    }
  }

  private def toTestData(removeTag: String)(code: String): SimpleTestData =
    testDataFromCode(
      code.linesIterator
        .map {
          case line if line.contains(removeTag) => line.take(line.indexOf("//").max(0))
          case line => line
        }
        .map(_.replace("[Scala2]", "").replace("[Scala3]", ""))
        .mkString("\n")
    )
}

abstract class WideningTestBase(minScalaVersion: ScalaVersion) extends GeneratedHighlightingParameterizedTest(minScalaVersion) {
  override type TD = SimpleTestData

  override def testData: Seq[TD] =
    if (version.isScala2) WideningTest.testDataInScala2
    else WideningTest.testDataInScala3
}

class WideningTest_Scala2 extends WideningTestBase(ScalaVersion.Latest.Scala_2_13)
class WideningTest_Scala3 extends WideningTestBase(ScalaVersion.Latest.Scala_3_LTS)