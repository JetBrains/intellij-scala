package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.patterns.{PsiJavaPatterns, PsiMethodPattern}
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.jetbrains.plugins.scala.patterns.ScalaPatterns

//SCL-24947 (with code examples from the ticket)
class ScalaLanguageInjectorTest_CallArgumentPattern2 extends ScalaLanguageInjectorTest_CallArgumentPatternBase {

  private val ticketSqlLiteral = "insert into person (name, age) values (?, ?)"

  override def setUp(): Unit = {
    super.setUp()

    val pattern: PsiMethodPattern = PsiJavaPatterns.psiMethod()
      .withName("apply")
      .withParameters("java.lang.String", "int")
      .definedInClass("org.example.MyUpdate$")
    registerRegexpCallArgumentPattern(new InjectionPlace(ScalaPatterns.scalaLiteral().callArgument(0, pattern), true))
  }

  private def doTicketExampleTest(callExpression: String): Unit = {
    doRegexpInjectionTest(
      s"""package org.example
         |
         |trait MyUpdate
         |
         |object MyUpdate {
         |  def apply[A](sql: String, pos: Int = 0): String = ???
         |}
         |
         |case class PersonInfo(name: String, age: Int)
         |
         |object Usage {
         |  import org.example.MyUpdate
         |
         |  $callExpression
         |}
         |""".stripMargin,
      ticketSqlLiteral
    )
  }

  // SCL-24947 ticket examples
  def testPatternInjection_CallArgument_TicketExample_NonGenericCall_OneArgument(): Unit =
    doTicketExampleTest("""MyUpdate.apply("insert into person (name, age) values (?, ?)")""")

  def testPatternInjection_CallArgument_TicketExample_NonGenericCall_TwoArguments(): Unit =
    doTicketExampleTest("""MyUpdate.apply("insert into person (name, age) values (?, ?)", 42)""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithoutApply_OneArgument(): Unit =
    doTicketExampleTest("""MyUpdate[PersonInfo]("insert into person (name, age) values (?, ?)")""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithoutApply_TwoArguments(): Unit =
    doTicketExampleTest("""MyUpdate[PersonInfo]("insert into person (name, age) values (?, ?)", 42)""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithoutApply_NamedArgument_OriginalOrder(): Unit =
    doTicketExampleTest("""MyUpdate[PersonInfo](sql = "insert into person (name, age) values (?, ?)", pos = 42)""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithoutApply_NamedArgument_ChangedOrder(): Unit =
    doTicketExampleTest("""MyUpdate[PersonInfo](pos = 42, sql = "insert into person (name, age) values (?, ?)")""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithApply_OneArgument(): Unit =
    doTicketExampleTest("""MyUpdate.apply[PersonInfo]("insert into person (name, age) values (?, ?)")""")

  def testPatternInjection_CallArgument_TicketExample_GenericCallWithApply_TwoArguments(): Unit =
    doTicketExampleTest("""MyUpdate.apply[PersonInfo]("insert into person (name, age) values (?, ?)", 42)""")
}
