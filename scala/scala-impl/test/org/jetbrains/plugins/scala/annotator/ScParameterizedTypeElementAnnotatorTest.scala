package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.ScParameterizedTypeElementAnnotatorTestBase.messagesForParameterizedTypeElements
import org.jetbrains.plugins.scala.annotator.element.ScParameterizedTypeElementAnnotator
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
trait ScParameterizedTypeElementAnnotatorTestBase extends SimpleTestCase {
  protected def scalaVersion: ScalaVersion

  def messages(code: String): List[Message] = {
    val file: ScalaFile = parseScalaFile(code, scalaVersion)
    messagesForParameterizedTypeElements(file)
  }
}

object ScParameterizedTypeElementAnnotatorTestBase {
  def messagesForParameterizedTypeElements(file: PsiFile): List[Message] = {
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScParameterizedTypeElement].foreach { pte =>
      ScParameterizedTypeElementAnnotator.annotate(pte, typeAware = true)
    }

    mock.annotations
  }
}

class ScParameterizedTypeElementAnnotatorTest_scala_2 extends ScParameterizedTypeElementAnnotatorTestBase {
  import Message._

  override protected val scalaVersion: ScalaVersion = ScalaVersion.default

  def testTooFewTypeParameter(): Unit = {
    assertMessagesInAllContexts("Test[Int]")(
      Error("t]", "Unspecified type parameters: Y")
    )
    assertMessagesInAllContexts("Test[]")(
      Error("[]", "Unspecified type parameters: X, Y")
    )
  }

  def testTooManyTypeParameter(): Unit = {
    assertMessagesInAllContexts("Test[Int, Int, Int]")(
      Error(", I", "Too many type arguments for Test, expected: 2, found: 3")
    )
    assertMessagesInAllContexts("Test[Int, Int, Boolean, Int]")(
      Error(", B", "Too many type arguments for Test, expected: 2, found: 4")
    )

    assertMessagesInAllContexts("A[Int]")(
      Error("[Int]", "A does not take type arguments")
    )
  }

  def testBoundViolation(): Unit = {
    assertMessagesInAllContexts("Test2[A, A]")(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
    )

    assertMessagesInAllContexts("Test2[C, C]")(
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )

    assertMessagesInAllContexts("Test2[A, C]")(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )
  }

  def testHigherKindedTypes(): Unit = {
    assertMessagesInAllContexts("TestHk[Test]")()

    assertMessagesInAllContexts("TestHk[A]")(
      Error("A", "Expected type constructor CC[X >: B <: B, _]")
    )

    assertMessagesInAllContexts("TestHk[HkArg]")(
      Error("HkArg", "Type constructor HkArg does not conform to CC[X >: B <: B, _]")
    )
  }

  def testTypeConstructorParameter(): Unit = {
    assertNothing(messages(
      """
        |trait Functor[F[_]]
        |
        |trait Applicative[G[_]] extends Functor[G]
        |
        |""".stripMargin
    ))
  }

  def testInferredUpperbound(): Unit = {
    assertNothing(messages(
      """
        |trait A
        |trait B extends A
        |
        |class Test[Up, X <: Up]
        |
        |new Test[A, B]
        |
        |""".stripMargin
    ))
  }

  def testSelfInUpperBound(): Unit = {
    assertNothing(messages(
      """
        |trait Base[A]
        |class Impl extends Base[Impl]
        |
        |class Test[X <: Base[X]]
        |
        |
        |new Test[Impl]
        |
        |""".stripMargin
    ))
  }

  def testOuterTypeParameter(): Unit = {
    assertMessages(messages(
      """
        |class Context[C <: Ctx, Ctx] {
        |
        |  class Test[X >: Ctx <: Ctx]
        |
        |  new Test[C]
        |}
        |""".stripMargin
    ))(
      Error("C", "Type C does not conform to lower bound Ctx of type parameter X")
    )
  }

  def testHkBound(): Unit = {

    assertNothing(messages(
      """
        |trait M[F]
        |trait S[X[A] <: M[X]]
        |
        |class Test[Y[_] <: M[Y]] extends S[Y]
        |""".stripMargin
    ))
  }

  def testTypeBoundContainingTypeParamInTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |trait Trait[X]
        |trait HkType[A[X <: Trait[X]]] {
        |    def test[B[Y <: Trait[Y]]]: HkType[B]
        |}
        |""".stripMargin
    ))
  }

  def testProjectionTypes(): Unit = {
    assertNothing(messages(
      """
        |sealed trait GenericList[U] {
        |  type Transformed[N <: U]
        |}
        |
        |trait GenericCons[U] {
        |  type Transformed[N <: U] = GenericList[U]#Transformed[N]
        |}
        |""".stripMargin
    ))
  }

  def testAnyAsTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |trait Hk[TC[_]]
        |
        |new Hk[Any]
        |""".stripMargin
    ))
  }

  def testNothingAsTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |trait Hk[TC[_]]
        |
        |new Hk[Nothing]
        |""".stripMargin
    ))
  }

  def testWildcard(): Unit = {
    assertNothing(messages(
      """
        |trait Growable
        |trait LazyCombiner[Buff <: Growable]
        |
        |object Test {
        |  null.isInstanceOf[LazyCombiner[_]]
        |}
        |
        |""".stripMargin
    ))
  }

  def testExistentialType(): Unit = {
    assertNothing(messages(
      """
        |trait Growable
        |trait LazyCombiner[Buff <: Growable]
        |
        |object Test {
        |  null.isInstanceOf[LazyCombiner[X] forSome { type X }]
        |}
        |
        |""".stripMargin
    ))
  }

  def testUnresolved(): Unit = {
    assertNothing(messages(
      """
        |trait A
        |trait B extends A
        |
        |trait Test[X <: A]
        |new Test[hahaha]
        |
        |""".stripMargin
    ))
  }

  def testTypePattern(): Unit = {
    assertNothing(messages(
      """
        |trait Bar
        |class Foo[t <: Bar] {}
        |
        |new Foo[Bar] match {
        |  case _ : Foo[x] => null: Foo[x]
        |}
        |""".stripMargin
    ))
  }

  def testUpperBoundIsTypeConstructorSetToAny(): Unit = {
    assertNothing(messages(
      """
        |trait Test[X <: Upper[X], Upper[_]]
        |
        |def test[T]: Test[T, Any]
        |""".stripMargin
    ))
  }

  def testLowerBoundIsTypeConstructorSetToNothing(): Unit = {
    assertNothing(messages(
      """
        |trait A
        |trait Test[X >: Lower[X], Lower[_]]
        |
        |def test[T >: A]: Test[T, Nothing]
        |""".stripMargin
    ))
  }

  def testGenericTraversalTemplate(): Unit = {
    assertNothing(messages(
      """
        |// Streamlined version of tcpoly_infer_implicit_tuple_wrapper.scala
        |trait Iterable[A]
        |trait GenericTraversableTemplate[DD[X] <: Iterable[X]]
        |
        |class IterableOps[CC[B] <: Iterable[B] with GenericTraversableTemplate[CC]]
        |
        |""".stripMargin
    ))
  }

  def testParameterizedProjectionType(): Unit = {
    assertNothing(messages(
      """
        |trait ParIterableLike[+TT] {
        |  class Copy[UU >: TT]
        |}
        |
        |trait ParSeqLike[+T] extends ParIterableLike[T] {
        |  def test[U >: T]() = {
        |    new Copy[U]
        |  }
        |}
        |""".stripMargin
    ))
  }

  def testBoundTypeConstructorInUpperBoundOfTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |trait SetLike[K, +This <: SetLike[K, This]]
        |trait SetFactory[CC[A] <: SetLike[A, CC[A] /* <- interesting */]]
        |
        |// Interesting check is
        |//   CC[A] <: SetLike[A, CC[A]]
        |// which should check upper bound of CC, which should result in the following check:
        |//   [A] SetLike[A, CC[A]] <: [A] SetLike[A, CC[A]]
        |""".stripMargin
    ))
  }

  def testSCL22501(): Unit = assertNothing(messages(
    """
      |trait MyIterable[+A]
      |
      |trait Aggregate1[F[_] <: MyIterable[_]]
      |trait Aggregate2[F[A] <: MyIterable[A]]
      |
      |//noinspection NotImplementedCode
      |object Example {
      |  //without type alias
      |  val aOk1: Aggregate1[MyIterable] = ???
      |  val aOk2: Aggregate2[MyIterable] = ???
      |
      |  type AggregateValue[T] <: MyIterable[T]
      |
      |  //with type alias
      |  val aBad1: Aggregate1[AggregateValue] = ???
      |  val aBad2: Aggregate2[AggregateValue] = ???
      |}
      |""".stripMargin
  ))

  def assertMessagesInAllContexts(typeText: String)(expected: Message*): Unit = {
    val Header =
      """
        |trait A
        |trait B extends A
        |trait C extends B
        |
        |class Test[X, Y]
        |class Test2[X <: B, Y >: B]
        |class TestHk[CC[X >: B <: B, _]]
        |
        |class HkArg[X >: A, Y <: C]
        |""".stripMargin

    val contexts = Seq(
      s"type W = $typeText",
      s"def w(arg: $typeText): Unit = ()",
      s"def w[Q](arg: $typeText): Unit = ()",
      s"def w(): $typeText = w()",
      s"new $typeText",
      s"class Blub extends $typeText",
      s"class W[Q <: $typeText]",
    )

    for (context <- contexts) {
      assertMessages(messages(Header + context))(expected: _*)
    }
  }
}

class ScParameterizedTypeElementAnnotatorTest_scala_3 extends ScParameterizedTypeElementAnnotatorTestBase {
  override protected val scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

  def testTypeLambdaAsTypeConstuctor(): Unit = assertNothing(messages(
    """
      |trait List[A]
      |trait Foo
      |
      |def foo[F[_], A](fa: F[A]): F[A] = fa
      |foo[[X] =>> List[X], Int](???)
      |type TL = [A] =>> Foo
      |foo[TL, Int](???)
      |type TL2 = [X] =>> [Y] =>> List[X]
      |foo[TL2[Foo], Int](???)
      |type TL3[A] = [B, D] =>> [C] =>> Int
      |foo[TL3[Int][Foo, Double], Int](???)
      |type TL4 = TL
      |foo[TL4, Foo](???)
      |""".stripMargin
  ))

  def testSCL20948(): Unit = assertNothing(messages(
    """
      |class Example[T <: Tuple]
      |class A() extends Example[(Int, Long)]
      |""".stripMargin
  ))

  def testSCL20610(): Unit = assertNothing(messages(
    """
      |object A {
      |  Right("Foo").withLeft[String]
      |}
      |""".stripMargin
  ))

  def testSCL20735(): Unit = assertNothing(messages(
    """
      |object BB {
      |  class N[A <: N[A]] {
      |    self: A =>
      |  }
      |
      |  class E[A <: N[A], B <: N[B], EE[
      |      AA <: N[AA],
      |      BB <: N[BB],
      |  ] <: E[AA, BB, EE]]
      |
      |  class A extends N[A]
      |
      |  class B extends N[B]
      |
      |  class C[A <: N[A], B <: N[B]] extends E[A, B, C]
      |
      |  def foo[
      |      A <: N[A],
      |      B <: N[B],
      |      EE[
      |          AA <: N[AA],
      |          BB <: N[BB],
      |      ] <: E[AA, BB, EE],
      |  ](a: A, b: B, c: EE[A, B]): EE[A, B] = ???
      |
      |  val a: C[A, B] = foo[A, B, C](new A, new B, new C[A, B])
      |}""".stripMargin
  ))

  def testSCL21054(): Unit = assertNothing(messages(
    """
      |trait Fish[E, Q <: Fish[E, Q]]
      |trait Cheese[C <: Fish[_, C]]
      |""".stripMargin
  ))

  def testSCL20981(): Unit = assertNothing(messages(
    """
      |case class Row()
      |def xxx[X <: Product with Serializable](x: X): Unit = ???
      |xxx[Row]""".stripMargin
  ))
}

class ScParameterizedTypeElementAnnotatorTest_Highlighting_scala_3 extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testSCL22257(): Unit = assertNothing(errorsFromScala3Code(
    """
      |import scala.compiletime.summonAll
      |import scala.deriving.Mirror
      |
      |inline def foo[T](using m: Mirror.SumOf[T]): Unit = {
      |  val elemInstances = summonAll[Tuple.Map[m.MirroredElemTypes, ValueOf]].productIterator
      |    .asInstanceOf[Iterator[ValueOf[T]]]
      |    .map(_.value)
      |  val elemNames = summonAll[Tuple.Map[m.MirroredElemLabels, ValueOf]].productIterator
      |    .asInstanceOf[Iterator[ValueOf[String]]]
      |    .map(_.value)
      |}
      |""".stripMargin
  ))
}

@Category(Array(classOf[TypecheckerTests]))
class ScParameterizedTypeElementAnnotatorTest_with_java extends ScalaLightCodeInsightFixtureTestCase with MatcherAssertions {

  def messages(code: String) = {
    val file = myFixture.addFileToProject("Test.scala", code)
    messagesForParameterizedTypeElements(file)
  }

  // SCL-19362
  def testGenericBoundedByObjectInJava(): Unit = {
    myFixture.addFileToProject("Base.java", "class Base<A extends Object> {}")

    assertNothing(messages(
      """
        |class Test[T] extends Base[T]
        |""".stripMargin
    ))
  }
}
