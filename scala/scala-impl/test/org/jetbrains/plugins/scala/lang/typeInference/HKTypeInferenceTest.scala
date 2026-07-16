package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.typeInference.HKTypeInferenceTest_Scala2.selectUnification
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.{GeneratedHighlightingParameterizedTest, GeneratedParameterizedTestFactory}

abstract class HKTypeInferenceTestBase(factory: HKTypeInferenceTestFactory) extends GeneratedHighlightingParameterizedTest(factory.version) {
  override type TD = SimpleTestData

  override val testData: Seq[SimpleTestData] = factory.testData

  override def supportedIn(version: ScalaVersion): Boolean =
    version == factory.version
}

abstract class HKTypeInferenceTestFactory(
  val version: ScalaVersion,
  _testData: Seq[String],
) {
  val testData: Seq[SimpleTestData] = _testData.map(GeneratedParameterizedTestFactory.testDataFromCode)
}

class HKTypeInferenceTest_Scala2_without_PartialUnification extends HKTypeInferenceTestBase(HKTypeInferenceTest_Scala2_with_PartialUnification)

object HKTypeInferenceTest_Scala2_without_PartialUnification extends HKTypeInferenceTestFactory(
  ScalaVersion.Latest.Scala_2_12,
  HKTypeInferenceTest_Scala2.testData.map(selectUnification("no"))
)


class HKTypeInferenceTest_Scala2_with_PartialUnification extends HKTypeInferenceTestBase(HKTypeInferenceTest_Scala2_with_PartialUnification)

object HKTypeInferenceTest_Scala2_with_PartialUnification extends HKTypeInferenceTestFactory(
  ScalaVersion.Latest.Scala_2_13,
  HKTypeInferenceTest_Scala2.testData.map(selectUnification("yes"))
)


object HKTypeInferenceTest_Scala2 {
  lazy val testData: Seq[String] = Seq(
    """
      |// withClass
      |trait A[T]
      |trait B[T]
      |class Y extends A[Int] with B[String]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: A[Option[Int]] = result
      |""".stripMargin,
    """
      |// typeAlias
      |trait A[T]
      |trait B[T]
      |type AInt = A[Int]
      |class Y extends AInt with B[String]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: A[Option[Int]] = result
      |""".stripMargin,
    """
      |// withClassIndirect
      |trait A[T]
      |trait B[T]
      |trait C[T]
      |trait AB extends A[Int] with B[String]
      |class Y extends AB with C[Boolean]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: C[Option[Boolean]] = result
      |""".stripMargin,
    """
      |// arity2
      |trait A[T, U]
      |trait B[T, U]
      |class Y extends A[Int, String] with B[Long, Double]
      |
      |def test[X[_, _], T, U](x: X[T, U]): X[Some[T], Option[U]] = ???
      |
      |val result = test(new Y)
      |val check: A[Some[Int], Option[String]] = result
      |""".stripMargin,
    """
      |// arityFilter
      |trait A[T, U]
      |trait B[T]
      |class Y extends A[Int, String] with B[Long]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: B[Option[Long]] = result        // unification:no
      |val check: A[Int, Option[String]] = result // unification:yes
      |""".stripMargin,
    """
      |// nested
      |trait A[T, U]
      |trait B[T]
      |trait C[T]
      |trait D[T]
      |trait CD extends C[Long] with D[Double]
      |trait BCD extends CD with B[String]
      |class Y extends BCD with A[Int, Boolean]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: B[Option[String]] = result       // unification:no
      |val check: A[Int, Option[Boolean]] = result // unification:yes
      |""".stripMargin,
    """
      |// throughAlias (SCL-22562: the argument's type is an alias to a parameterized type)
      |trait A[T]
      |type AA = A[Int]
      |def make: AA = ???
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(make)
      |val check: A[Option[Int]] = result
      |""".stripMargin,
  )

  def selectUnification(marker: String)(code: String): String = {
    assert(!code.contains("unification:") || (code.contains(s"unification:yes") && code.contains("unification:no")))
    code.linesWithSeparators
      .filterNot(line => line.contains("unification:") && !line.contains(s"unification:$marker"))
      .mkString
  }
}

class HKTypeInferenceTest_Scala3 extends HKTypeInferenceTestBase(HKTypeInferenceTest_Scala3)

object HKTypeInferenceTest_Scala3 extends HKTypeInferenceTestFactory(
  ScalaVersion.Latest.Scala_3,
  Seq(
    """
      |// withClass
      |trait A[T]
      |trait B[T]
      |class Y extends A[Int] with B[String]
      |
      |def test[X[_], T](x: X[T]): X[T] = x
      |
      |val result = test(new Y)
      |val check: B[String] = result
      |""".stripMargin,
    """
      |// typeAlias
      |trait A[T]
      |trait B[T]
      |type BString = B[String]
      |class Y extends A[Int] with BString
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: B[Option[String]] = result
      |""".stripMargin,
    """
      |// withClassIndirect
      |trait A[T]
      |trait B[T]
      |trait C[T]
      |trait BC extends B[String] with C[Boolean]
      |class Y extends A[Int] with BC
      |
      |def test[X[_], T](x: X[T]): X[T] = x
      |
      |val result = test(new Y)
      |val check: C[Boolean] = result
      |""".stripMargin,
    """
      |// ofIntersectionType
      |trait A[T]
      |trait B[T]
      |class Y extends A[Int] with B[String]
      |
      |def test[X[_], T](x: X[T]): X[T] = x
      |
      |val result = test(new Y: A[Int] & B[String])
      |val check: A[Int] = result
      |""".stripMargin,
    """
      |// arity2
      |trait A[T, U]
      |trait B[T, U]
      |class Y extends A[Int, String] with B[Long, Double]
      |
      |def test[X[_, _], T, U](x: X[T, U]): X[T, U] = x
      |
      |val result = test(new Y)
      |val check: B[Long, Double] = result
      |""".stripMargin,
    """
      |// arity2Indirect
      |trait A[T, U]
      |trait B[T, U]
      |trait C[T, U]
      |trait BC extends B[String, Long] with C[Char, Double]
      |class Y extends A[Int, String] with BC
      |
      |def test[X[_, _], T, U](x: X[T, U]): X[Some[T], Option[U]] = ???
      |
      |val result = test(new Y)
      |val check: C[Some[Char], Option[Double]] = result
      |""".stripMargin,
    """
      |// arityFilter
      |trait A[T, U]
      |trait B[T]
      |class Y extends B[Long] with A[Int, String]
      |
      |def test[X[_], T](x: X[T]): X[Option[T]] = ???
      |
      |val result = test(new Y)
      |val check: A[Int, Option[String]] = result
      |""".stripMargin,
    """
      |// deepNesting
      |trait A[T]
      |trait B[T]
      |trait C[T]
      |trait D[T]
      |trait CD extends C[Long] with D[Double]
      |trait BCD extends B[String] with CD
      |class Y extends A[Int] with BCD
      |
      |def test[X[_], T](x: X[T]): X[T] = x
      |
      |val result = test(new Y)
      |val check: D[Double] = result
      |""".stripMargin,
    """
      |// throughAlias (SCL-22562: the argument's type is an alias to a parameterized type)
      |trait A[T]
      |type AA = A[Int]
      |def make: AA = ???
      |
      |def test[X[_], T](x: X[T]): X[T] = x
      |
      |val result = test(make)
      |val check: A[Int] = result
      |""".stripMargin,
  )
)
