package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase
import org.jetbrains.plugins.scala.lang.dfa.Messages.ConditionAlwaysFalse
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem.{npeOnInvocation, nullableToUnannotatedParam}
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3, TestScalaVersion.Scala_3_9))
class Scala3EnumDfaTest extends ScalaDfaTestBase {

  @Test
  def test_singleton_case_argument(): Unit = test(
    """enum PowerLevel:
      |  case Medium
      |
      |case class Superhero(powerLevel: PowerLevel)
      |
      |def foo(): Unit = {
      |  val updatedPowerLevel = PowerLevel.Medium
      |  Superhero(updatedPowerLevel)
      |}
      |""".stripMargin
  )()

  @Test
  def test_exhaustive_match(): Unit = testWithUnsupportedPsiElements(
    """enum PowerLevel:
      |  case Weak, Medium, Strong, Hulk
      |
      |final case class Superhero(name: String, powerLevel: PowerLevel)
      |
      |def powerUp(s: Superhero): Superhero = {
      |  val updatedPowerLevel =
      |    s.powerLevel match
      |      case PowerLevel.Weak => PowerLevel.Medium
      |      case PowerLevel.Medium => PowerLevel.Strong
      |      case PowerLevel.Strong => PowerLevel.Hulk
      |      case PowerLevel.Hulk => PowerLevel.Hulk
      |  Superhero(s.name, updatedPowerLevel)
      |}
      |""".stripMargin
  )()

  @Test
  def test_class_case_argument(): Unit = test(
    """enum PowerLevel:
      |  case Custom(value: Int)
      |
      |case class Superhero(powerLevel: PowerLevel)
      |
      |def foo(): Unit = {
      |  val updatedPowerLevel = PowerLevel.Custom(42)
      |  Superhero(updatedPowerLevel)
      |}
      |""".stripMargin
  )()

  @Test
  def test_generic_enum_case_argument(): Unit = test(
    """enum Result[+A]:
      |  case Empty
      |  case Value(value: A)
      |
      |case class Wrapper(result: Result[Int])
      |
      |def foo(): Unit = {
      |  val empty = Result.Empty
      |  val value = Result.Value(42)
      |  Wrapper(empty)
      |  Wrapper(value)
      |}
      |""".stripMargin
  )()

  @Test
  def test_null_enum_argument(): Unit = test(
    """enum PowerLevel:
      |  case Medium
      |
      |case class Superhero(powerLevel: PowerLevel)
      |
      |def foo(): Unit = {
      |  val powerLevel: PowerLevel = null
      |  Superhero(powerLevel)
      |}
      |""".stripMargin
  )(
    "powerLevel" -> nullableToUnannotatedParam.alwaysMessage
  )

  @Test
  def test_enum_cases_from_branches(): Unit = test(
    """trait Powered
      |
      |enum PowerLevel extends Powered:
      |  case Medium
      |  case Custom(value: Int)
      |
      |case class Superhero(powerLevel: PowerLevel)
      |case class Wrapper(powered: Powered)
      |
      |def foo(custom: Boolean): Unit = {
      |  val updatedPowerLevel = if (custom) PowerLevel.Custom(42) else PowerLevel.Medium
      |  Superhero(updatedPowerLevel)
      |  Wrapper(updatedPowerLevel)
      |}
      |""".stripMargin
  )()

  @Test
  def test_nullable_enum_parameter(): Unit = test(
    """import org.jetbrains.annotations.Nullable
      |
      |enum PowerLevel:
      |  case Medium
      |
      |def foo(@Nullable powerLevel: PowerLevel): Unit = {
      |  powerLevel.toString
      |}
      |""".stripMargin
  )(
    "toString" -> npeOnInvocation.sometimesMessage
  )

  @Test
  def test_not_null_enum_parameter(): Unit = test(
    """import org.jetbrains.annotations.NotNull
      |
      |enum PowerLevel:
      |  case Medium
      |
      |def foo(@NotNull powerLevel: PowerLevel): Boolean = {
      |  powerLevel == null
      |}
      |""".stripMargin
  )(
    "powerLevel == null" -> ConditionAlwaysFalse
  )
}
