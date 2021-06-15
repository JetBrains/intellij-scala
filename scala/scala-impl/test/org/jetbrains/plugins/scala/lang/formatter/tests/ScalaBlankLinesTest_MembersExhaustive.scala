package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.jdk.CollectionConverters.SeqHasAsJava

@RunWith(classOf[Parameterized])
class ScalaBlankLinesTest_MembersExhaustive(tuple: (String, String, String))
  extends AbstractScalaFormatterTestBase with LineCommentsTestOps {

  private val (first, second, container) = tuple

  // Test huge amount combinations of members in different contexts with different blank lines settings
  // example:
  // trait T {
  //   def foo = ????
  //   val x = 42
  // }
  @Test
  def testBlankLines(): Unit = {
    val before =
      s"""$container {
         |  $first
         |  $second
         |}""".stripMargin

    allBlankLinesSettingsCombinations(first, second, container) { () =>
      val expectedLines = expectedLinesBetween(first, second, container)
      val expectedAfter =
        s"""$container {
           |  $first${"\n" * expectedLines}
           |  $second
           |}""".stripMargin
      try doTextTest(before, expectedAfter) catch {
        case ex: Throwable =>
          System.err.println(s"### SETTINGS ###\n$currentBlankLinesSettingsDebugText")
          throw ex
      }
    }
  }

  /** @return Cartesian product */
  private def prod[T](l: List[List[T]]): List[List[T]] = l match {
    case Nil     => List(Nil)
    case l :: ls => for (i <- l; r <- prod(ls)) yield i :: r
  }

  // size of "fair" product of all possible settings values is very huge, and tests would take ages to end
  // so we do some optimisation to drop irrelevant settings in some cases
  private def allBlankLinesSettingsCombinations(first: String, second: String, container: String)(body: () => Unit): Unit = {
    val fSetter  : Int => Unit = cs.BLANK_LINES_AROUND_FIELD = _
    val fiSetter : Int => Unit = cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = _
    val fisSetter: Int => Unit = ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = _
    val mSetter  : Int => Unit = cs.BLANK_LINES_AROUND_METHOD = _
    val miSetter : Int => Unit = cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = _
    val misSetter: Int => Unit = ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = _
    val cSetter  : Int => Unit = cs.BLANK_LINES_AROUND_CLASS = _
    val cisSetter: Int => Unit = ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = _

    val memberTypes = firstToken(first) :: firstToken(second) :: Nil

    val containerType = firstToken(container)

    val allSetters = List(fSetter, fiSetter, fisSetter, mSetter, miSetter, misSetter, cSetter, cisSetter)

    val containerRelevantSetters = containerType match {
      case "class" | "object" => Seq(fSetter, mSetter, cSetter)
      case "trait"            => Seq(fiSetter, miSetter, cSetter)
      case "def"              => Seq(fisSetter, misSetter, cisSetter)
    }
    val containerNotRelevantSetters = allSetters.diff(containerRelevantSetters)

    val relevantSetters: List[Int => Unit] = memberTypes.flatMap {
      case "class" | "trait" | "object"        => List(cSetter, cisSetter)
      case "def"                               => List(mSetter, miSetter, misSetter)
      case "val" | "var" | "lazy" | "type" | _ => List(fSetter, fiSetter, fisSetter)
    }.distinct.diff(containerNotRelevantSetters)

    val notRelevantSetters = allSetters.diff(relevantSetters).diff(containerNotRelevantSetters)

    assert(allSetters.size == relevantSetters.size + notRelevantSetters.size + containerNotRelevantSetters.size)

    val maxBlankLine = 2

    def applications(setters: List[Int => Unit]): List[List[() => Unit]] =
      setters.map(setter => (0 to maxBlankLine).map(idx => () => setter.apply(idx)).toList)

    val relevantApplications = applications(relevantSetters)
    val notRelevantApplications1 = applications(notRelevantSetters)
    val notRelevantApplications2 = applications(containerNotRelevantSetters)

//    val prod1 = prod(relevantApplications ++ notRelevantApplications1)
//    val prod2 = prod(relevantApplications ++ notRelevantApplications2)

    val prod1 = prod(relevantApplications)
    val prod2 = prod(notRelevantApplications1)
    val prod3 = prod(notRelevantApplications2)

    val allProd = Seq(prod1, prod2, prod3).flatten

    allSetters.foreach(_.apply(0))

    allProd.foreach { applySettings: Seq[() => Unit] =>
      applySettings.foreach(_.apply())
      body()
    }
  }

  private def expectedLinesBetween(firstMember: String, secondMember: String, container: String): Int = {
    val firstValue = settingValue(firstMember, container)
    val secondValue = settingValue(secondMember, container)
    math.max(firstValue, secondValue)
  }

  //noinspection ScalaUnusedSymbol
  private def settingValue(member: String, container: String): Int =
    firstToken(member) match {
      case "class" | "trait" | "object" =>
        firstToken(container) match {
          case "def" => ss.BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES
          case _     => cs.BLANK_LINES_AROUND_CLASS
        }
      case "def" =>
        firstToken(container) match {
          case "class" | "object" => cs.BLANK_LINES_AROUND_METHOD
          case "trait"            => cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
          case "def"              => ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES
        }
      case "val" | "var" | "lazy" | "type" | _ =>
        firstToken(container) match {
          case "class" | "object" => cs.BLANK_LINES_AROUND_FIELD
          case "trait"            => cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
          case "def"              => ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES
        }
    }

  private def firstToken(member: String): String = {
    val idx = member.indexOf(' ')
    if (idx != -1) member.substring(0, member.indexOf(' '))
    else member
  }

  private def currentBlankLinesSettingsDebugText: String =
    s"""BLANK_LINES_AROUND_FIELD                 = ${cs.BLANK_LINES_AROUND_FIELD}
       |BLANK_LINES_AROUND_FIELD_IN_INTERFACE    = ${cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE }
       |BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = ${ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES}
       |
       |BLANK_LINES_AROUND_METHOD                 = ${cs.BLANK_LINES_AROUND_METHOD}
       |BLANK_LINES_AROUND_METHOD_IN_INTERFACE    = ${cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE }
       |BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = ${ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES}
       |
       |BLANK_LINES_AROUND_CLASS                  = ${cs.BLANK_LINES_AROUND_CLASS}
       |BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES  = ${ss.BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES}
       |""".stripMargin
}

object ScalaBlankLinesTest_MembersExhaustive {

  private val members1 = Seq(
    "val a = 1",
    "var a = 1",
    "lazy val a = 1",
    "type t = String"
  )
  private val members2 = Seq(
    "println(42)",
  )
  private val members3 = Seq(
    "def f = 1",
  )
  private val members4 = Seq(
    "class T",
    "trait T",
    "object T",
  )

  private val pairs: Seq[(String, String)] = Nil ++
    (for (a <- members1; b <- members2) yield Seq((a, b), (a, a))).flatten ++
    (for (a <- members1; b <- members3) yield Seq((a, b), (a, a))).flatten ++
    (for (a <- members1; b <- members4) yield Seq((a, b), (a, a))).flatten ++
    (for (a <- members2; b <- members3) yield Seq((a, b), (a, a))).flatten ++
    (for (a <- members2; b <- members4) yield Seq((a, b), (a, a))).flatten ++
    (for (a <- members3; b <- members4) yield Seq((a, b), (a, a))).flatten :+
    (members1.head, members1.head) :+
    (members2.head, members2.head) :+
    (members3.head, members3.head) :+
    (members4.head, members4.head)

  private val containers = Seq(
    "trait T",
    "class T",
    "def foo",
  )

  private val combinations: Seq[(String, String, String)] = for {
    (first, second) <- pairs
    container <- containers
  } yield (first, second, container)

  @Parameterized.Parameters(name = "{index}: {0}")
  def primeNumbers: java.util.Collection[(String, String, String)] =
    combinations.asJava
}
