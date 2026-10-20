package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.everyValueGreaterThanIn
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Changing the implementation of a macro must recompile the sources that expand it, even though the API of the macro
 * definition does not change. Scala 2 cannot expand a macro in the compilation run that defines it, so this scenario
 * only exists for Scala 3.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-26126 SCL-26126]]
 */
@RunWith(classOf[JUnit4])
abstract class MacroIncrementalCompilationTestBase(scalaVersion: ScalaVersion) extends IncrementalCompilationTestFixture {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  override protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  override protected def useCompileServer: Boolean = true

  @Test
  def testRecompileUsersOfChangedMacro(): Unit = {
    val sources = initBuildProject(
      new SourceFile(
        name = "Answer",
        classes = Set("Answer", "Answer$"),
        code =
          """
            |import scala.quoted.*
            |
            |object Answer:
            |  inline def answer: Int = ${ answerImpl }
            |  private def answerImpl(using Quotes): Expr[Int] = '{ 1 }
            |""".stripMargin
      ),
      new SourceFile(
        name = "User",
        classes = Set("User", "User$"),
        code =
          """
            |object User:
            |  val answer = Answer.answer
            |""".stripMargin
      )
    )
    val (answerTsBefore, userTsBefore) = targetTimestamps(sources)

    // Only the body of the macro implementation changes, the API of Answer stays the same
    sources.head.writeCode(
      classes = Set("Answer", "Answer$"),
      code =
        """
          |import scala.quoted.*
          |
          |object Answer:
          |  inline def answer: Int = ${ answerImpl }
          |  private def answerImpl(using Quotes): Expr[Int] = '{ 2 }
          |""".stripMargin
    )
    compiler.make().assertNoProblems()
    val (answerTsAfter, userTsAfter) = targetTimestamps(sources)

    assertThat("Answer hasn't been recompiled", answerTsAfter, everyValueGreaterThanIn(answerTsBefore))
    assertThat("User hasn't been recompiled", userTsAfter, everyValueGreaterThanIn(userTsBefore))
  }

  private def targetTimestamps(sources: Seq[SourceFile]): (Map[String, Long], Map[String, Long]) = {
    assertEquals(2, sources.size)
    val first = sources.head.targetTimestamps
    val second = sources(1).targetTimestamps
    (first, second)
  }
}

class MacroIncrementalCompilationTest_Scala_3_3 extends MacroIncrementalCompilationTestBase(ScalaVersion.Latest.Scala_3_3)

class MacroIncrementalCompilationTest_Scala_3_9 extends MacroIncrementalCompilationTestBase(ScalaVersion.Latest.Scala_3_9)

class MacroIncrementalCompilationTest_Scala_Next_RC extends MacroIncrementalCompilationTestBase(ScalaVersion.Latest.Scala_3_Next_RC)
