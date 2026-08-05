package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.{GeneratedHighlightingParameterizedTest, GeneratedParameterizedTestFactory}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-3825
@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionResultTypeTest extends GeneratedHighlightingParameterizedTest(ScalaVersion.Latest.Scala_2_13) {
  override type TD = SimpleTestData

  override def testData: Seq[SimpleTestData] = ImplicitConversionResultTypeTest.testData
}

object ImplicitConversionResultTypeTest {
  /**
   * The result type of an implicit conversion has to be ''more specific'' than the expected type,
   * which is never the case for `AnyRef`/`java.lang.Object` (and, since Scala 2.11, `AnyVal`),
   * so no conversion is searched for in that case.
   *
   * Note that `Long` is used as the source type of the conversions below on purpose:
   * `Predef.long2Long` is the only conversion from `Long` to an `AnyRef`, so the conversion
   * would be unambiguously applicable if it weren't for the expected type.
   */
  lazy val testData: Seq[SimpleTestData] = Seq(
    s"""
       |// conversionToAnyRefIsNotApplied
       |trait Sub {
       |  type Z = Long
       |}
       |val s: Sub = null
       |val a: s.Z = 1L
       |
       |val boxed: java.lang.Long = a
       |
       |val anyRefFromAlias: AnyRef = a    // Error
       |val anyRefFromLong: AnyRef = 1L    // Error
       |""".stripMargin,
    s"""
       |// conversionToJavaLangObjectIsNotApplied
       |val boxed: java.lang.Long = 1L
       |
       |val obj: Object = 1L    // Error
       |""".stripMargin,
    s"""
       |// conversionToAnyValIsNotApplied
       |import scala.language.implicitConversions
       |
       |class Box
       |class Meters(val value: Int) extends AnyVal
       |implicit def toMeters(b: Box): Meters = new Meters(1)
       |
       |val box = new Box
       |
       |val meters: Meters = box
       |
       |val anyVal: AnyVal = box    // Error
       |""".stripMargin,
    s"""
       |// conversionToMoreSpecificTypeIsApplied
       |import scala.language.implicitConversions
       |
       |class Wrapper(val i: Int)
       |implicit def wrap(i: Int): Wrapper = new Wrapper(i)
       |
       |val wrapper: Wrapper = 1
       |val refined: AnyRef { def i: Int } = 1
       |val any: Any = 1
       |val serializable: java.io.Serializable = 1
       |val comparable: Comparable[java.lang.Integer] = 1
       |""".stripMargin,
  ).map(GeneratedParameterizedTestFactory.testDataFromCode)
}
