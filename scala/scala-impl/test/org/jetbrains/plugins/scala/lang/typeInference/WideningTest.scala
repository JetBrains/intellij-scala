package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.GeneratedHighlightingParameterizedTest
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.{SimpleTestData, testDataFromVersionTaggedCode}

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
      |""".stripMargin.multi,
    """// NonWideningWithSAMExpectedType_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// The expected type of a lambda may be a SAM type instead of a function type, and it is the
      |// abstract method of the SAM that asks for the singleton result type #SCL-23271
      |enum E { case A, B }  [Scala3]
      |val c: Any = ???
      |
      |trait T { def apply(): <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> }
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |val t: T = () => x
      |""".stripMargin.multi
  ).flatten

  /** Test data that uses syntax which only exists in Scala 3 and is therefore not run in Scala 2. */
  private lazy val scala3OnlyTestData: Seq[String] = Seq(
    """// NonWideningInMatchType_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// Reducing a match type instantiates the type variables of its pattern, but in contrast to an
      |// inferred type argument, what a pattern captured must not be widened #SCL-23271
      |// `Wrapper` has to be covariant, otherwise the capture is bounded from above as well, which
      |// suppresses widening anyway. This is `Tuple.Head[tup.type]`, boiled down.
      |class Wrapper[+T]
      |enum E { case A, B }
      |val c: Any = ???
      |
      |type Unwrap[X] = X match { case Wrapper[t] => t }
      |
      |val w: Wrapper[<1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }>] = ???
      |val y: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ??? : Unwrap[w.type]
      |""".stripMargin.multi,
    """// WideningOfExtensionReceiver_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// The receiver of an extension method call instantiates the type parameters of the extension,
      |// which are therefore widened like any other inferred type argument #SCL-21053
      |class Test[T]
      |enum E { case A, B }
      |val c: Any = ???
      |
      |extension [X](x: X) def ext: Test[X] = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |val y = x.ext
      |val z: Test[<1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }>] = y // <Error, Error, Error, Error, fine, fine>
      |""".stripMargin.multi,
    """// WideningOfExtensionReceiverBeforeGivenSearch_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// The type parameters of an extension are instantiated before the givens of its using clauses
      |// are searched for, so the search has to be done with the widened receiver type #SCL-21053
      |trait C // we need C, otherwise the given search will be underspecified
      |enum E { case A, B }
      |val c: C = ???
      |trait Bar[T]
      |given Bar[<Int, String, C, E, C { def bar: Int }, reflect.Selectable { def bar: Int }>] = ???
      |
      |extension [X](x: X) def ext(using Bar[X]): Unit = ()
      |
      |val x: <1, "x", c.type, E.A.type, C { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |x.ext
      |""".stripMargin.multi,
    """// WideningBeforeApplyExpansion_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// The type parameters of a call are instantiated, and therefore widened, before `apply` is
      |// resolved on its result type. The overload is only decided by the second clause, so the
      |// widening has to happen while the alternatives are still being checked #SCL-23271
      |trait Bar[T] { def apply(t: T): Int = 123 }
      |enum E { case A, B }
      |val c: Any = ???
      |
      |// nothing conforms to `Marker`, so only the second alternative is applicable
      |class Marker
      |def foo[T](t: T)(marker: Marker): String = ???
      |def foo[T](t: T): Bar[T] = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |// the argument of `apply` only conforms to the widened type of `x`, so the alternative is
      |// only applicable if `T` was widened before `apply` was resolved on `Bar[T]`
      |foo(x)(<2, "y", c: Any, E.B, x, x>)
      |""".stripMargin.multi,
    """// WideningWithUndeterminedSAMResultType_<Literal,StringLit,DependentType,Enum,Structural,Selectable>
      |// The result type of the SAM only suppresses widening if it asks for a singleton type itself.
      |// An undetermined type parameter doesn't, it is instantiated to the widened type #SCL-23271
      |class Test[T]
      |trait Callable[T] { def call(): T }
      |def run[T](task: Callable[T]): Test[T] = ???
      |enum E { case A, B }
      |val c: Any = ???
      |
      |val x: <1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }> = ???
      |val y = run(() => x)
      |val z: Test[<1, "x", c.type, E.A.type, Object { def bar: Int }, reflect.Selectable { def bar: Int }>] = y // <Error, Error, Error, Error, fine, fine>
      |""".stripMargin.multi,
    """// NonWideningOfSingletonBoundedExtensionReceiver_<Literal,StringLit,Enum>
      |// A type parameter that asks for a singleton type is not widened, so the given of the using
      |// clause is searched for with the singleton receiver type #SCL-21053
      |enum E { case A, B }
      |trait Bar[T]
      |given Bar[<1, "x", E.A.type>] = ???
      |
      |extension [X <: Singleton](x: X) def ext(using Bar[X]): Unit = ()
      |
      |<1, "x", E.A>.ext
      |""".stripMargin.multi
  ).flatten

  lazy val testDataInScala2: Seq[SimpleTestData] =
    testData.map(toTestData("[Scala3]")).filterNot(_.testName.contains("Enum")).filterNot(_.testName.contains("Selectable"))
  lazy val testDataInScala3: Seq[SimpleTestData] =
    (testData ++ scala3OnlyTestData).map(toTestData("[Scala2]"))

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
    testDataFromVersionTaggedCode(removeTag)(code)
}

abstract class WideningTestBase(minScalaVersion: ScalaVersion) extends GeneratedHighlightingParameterizedTest(minScalaVersion) {
  override type TD = SimpleTestData

  override def testData: Seq[TD] =
    if (version.isScala2) WideningTest.testDataInScala2
    else WideningTest.testDataInScala3
}

class WideningTest_Scala2 extends WideningTestBase(ScalaVersion.Latest.Scala_2_13)
class WideningTest_Scala3 extends WideningTestBase(ScalaVersion.Latest.Scala_3_LTS)