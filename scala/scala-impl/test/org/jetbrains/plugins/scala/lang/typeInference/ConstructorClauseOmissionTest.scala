package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.{GeneratedHighlightingParameterizedTest, GeneratedParameterizedTestFactory}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

import java.lang.ref.Reference
import scala.compiletime.uninitialized

/** SCL-26339. The same cases are compiled by CheckConstructorClauseOmissionTest. */
object ConstructorClauseOmissionTest {
  lazy val testData: Seq[SimpleTestData] = Seq(
    """
      |// repeatedLeadingEmptyClauses
      |class Foo()()(using Int)
      |given Int = 1
      |
      |val inferred = new Foo
      |val explicit = new Foo(using 2)
      |val partiallyWritten = new Foo()(using 2)
      |val fullyWritten = new Foo()()(using 2)
      |class Parent extends Foo(using 2)
      |""".stripMargin,
    """
      |// laterEmptyClausesAfterOrdinaryArgument
      |class Foo()(i: Int)()()(using String)
      |given String = "s"
      |
      |val inferred = new Foo()(1)
      |val explicit = new Foo()(1)(using "s")
      |val partiallyWritten = new Foo()(1)()(using "s")
      |class Parent extends Foo()(1)(using "s")
      |""".stripMargin,
    """
      |// emptyClauseBetweenUsingClauses
      |class Foo()(using Int)()(using String)
      |given Int = 1
      |given String = "s"
      |
      |val inferred = new Foo
      |val explicit = new Foo(using 2)(using "s")
      |val partiallyExplicit = new Foo(using 2)
      |class Parent extends Foo(using 2)(using "s")
      |""".stripMargin,
    """
      |// trailingEmptyClausesAfterNonemptyFirstClause
      |class Foo(i: Int)()()
      |
      |val omitted = new Foo(1)
      |val partiallyWritten = new Foo(1)()
      |val fullyWritten = new Foo(1)()()
      |class Parent extends Foo(1)
      |""".stripMargin,
    """
      |// typeInferenceAcrossOmittedEmptyClauses
      |class Foo[T]()()(using T)
      |given Int = 1
      |
      |val inferred = new Foo(using 2)
      |val check: Foo[Int] = inferred
      |val fromExpectedType: Foo[Int] = new Foo
      |val withExplicitType = new Foo[Int]
      |val withExplicitTypeAndUsing = new Foo[Int](using 2)
      |""".stripMargin,
    """
      |// defaultedFirstClauseBeforeUsing
      |class Foo(i: Int = 1)(using String)
      |given String = "s"
      |
      |val inferred = new Foo
      |val explicit = new Foo()(using "s")
      |val missingClause = new Foo(using "s") // Error
      |""".stripMargin,
    """
      |// laterDefaultedClausesBeforeUsing
      |class AfterEmpty()(i: Int = 1)(using String)
      |class AfterNonempty(i: Int)(j: Int = 2)(using String)
      |class MultipleDefaults(i: Int = 1)(j: Int = 2)(using String)
      |given String = "s"
      |
      |val afterEmpty = new AfterEmpty()()(using "s")
      |val afterNonempty = new AfterNonempty(1)()(using "s")
      |val multipleDefaults = new MultipleDefaults()()(using "s")
      |val missingAfterEmpty = new AfterEmpty(using "s") // Error
      |val missingAfterNonempty = new AfterNonempty(1)(using "s") // Error
      |val missingDefaults = new MultipleDefaults(using "s") // Error
      |""".stripMargin,
    """
      |// nonemptyFirstClausePreventsOmissionBeforeUsing
      |class Foo(i: Int)()(using String)
      |given String = "s"
      |
      |val inferred = new Foo(1)
      |val explicit = new Foo(1)()(using "s")
      |val missingClause = new Foo(1)(using "s") // Error
      |""".stripMargin,
    """
      |// leadingUsingClausePreventsOmissionBeforeUsing
      |class Foo(using Int)()(using String)
      |given Int = 1
      |given String = "s"
      |
      |val inferred = new Foo
      |val explicit = new Foo(using 2)()(using "s")
      |val missingClause = new Foo(using 2)(using "s") // Error
      |""".stripMargin,
    """
      |// emptyClauseCannotBeOmittedBeforeOrdinaryArguments
      |class Foo()(i: Int)
      |
      |val explicit = new Foo()(1)
      |val missingClause = new Foo(1) // Error
      |""".stripMargin,
    """
      |// emptyMethodClausesCannotBeOmittedBeforeUsing
      |def foo()()(using Int): Unit = ()
      |
      |foo()()(using 1)
      |foo(using 1) // Error
      |foo()(using 1) // Error
      |""".stripMargin,
    """
      |// excessClausesAfterOmittedEmptyClauses
      |class Foo()()(using Int)
      |
      |val explicit = new Foo(using 1)
      |val excess = new Foo(using 1)() // Error
      |""".stripMargin
  ).map(GeneratedParameterizedTestFactory.testDataFromCode)
}

@Category(Array(classOf[TypecheckerTests]))
abstract class ConstructorClauseOmissionTestBase(scalaVersion: ScalaVersion)
  extends GeneratedHighlightingParameterizedTest(scalaVersion) {
  override type TD = SimpleTestData

  private var function1Tree: ASTNode = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()
    // Invalid partial constructor calls are recovered as function types. The abstract-member
    // diagnostic creates smart pointers into Function1, which requires its AST. Keep it alive
    // throughout highlighting so it cannot be collected and reloaded under the AST-loading guard.
    function1Tree = myFixture.findClass("scala.Function1").getContainingFile.getNode
  }

  override protected def tearDown(): Unit = try super.tearDown() finally {
    Reference.reachabilityFence(function1Tree)
    function1Tree = null
  }

  override def testData: Seq[SimpleTestData] = ConstructorClauseOmissionTest.testData

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion
}

final class ConstructorClauseOmissionTest_3_3 extends ConstructorClauseOmissionTestBase(LatestScalaVersions.Scala_3_3)
final class ConstructorClauseOmissionTest_3_9 extends ConstructorClauseOmissionTestBase(LatestScalaVersions.Scala_3_9)
