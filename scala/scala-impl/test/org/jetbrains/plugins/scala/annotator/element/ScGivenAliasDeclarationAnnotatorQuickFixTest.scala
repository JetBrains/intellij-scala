package org.jetbrains.plugins.scala.annotator.element

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ScGivenAliasDeclarationAnnotatorQuickFixTest extends ScalaAnnotatorQuickFixTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected val description: String = ScalaBundle.message("given.alias.declaration.must.be.named")
}

final class ImplementAnonymousAbstractGivenFixTest extends ScGivenAliasDeclarationAnnotatorQuickFixTest {
  private val hint: String = ScalaBundle.message("family.name.implement.anonymous.abstract.given")

  def testAnonymousAbstractGiven(): Unit = testQuickFix(
    text =
      s"""
         |trait Foo:
         |  given$CARET String
         |end Foo
         |""".stripMargin,
    expected =
      """
        |trait Foo:
        |  given String = ???
        |end Foo
        |""".stripMargin,
    hint = hint
  )
}

@WithIndexingMode(mode = IndexingMode.SMART, reason = "NameSuggester needs type inference")
final class NameAnonymousAbstractGivenFixTest extends ScGivenAliasDeclarationAnnotatorQuickFixTest {
  private val hint: String = ScalaBundle.message("family.name.give.a.name.to.anonymous.abstract.given")

  def testAnonymousAbstractGiven(): Unit = testQuickFix(
    text =
      s"""
         |trait Foo:
         |  given$CARET String
         |end Foo
         |""".stripMargin,
    expected =
      """
        |trait Foo:
        |  given str: String
        |end Foo
        |""".stripMargin,
    hint = hint
  )

  def testAnonymousAbstractGivenWithNameConflict(): Unit = testQuickFix(
    text =
      s"""
         |trait Foo:
         |  val str: String
         |  given$CARET String
         |end Foo
         |""".stripMargin,
    expected =
      """
        |trait Foo:
        |  val str: String
        |  given str1: String
        |end Foo
        |""".stripMargin,
    hint = hint
  )

  def testAnonymousAbstractGivenGeneratedName(): Unit = testQuickFix(
    text =
      s"""
         |trait A[T]
         |
         |trait Foo:
         |  given$CARET A[List[Int]]
         |end Foo
         |""".stripMargin,
    expected =
      """
        |trait A[T]
        |
        |trait Foo:
        |  given given_A_List: A[List[Int]]
        |end Foo
        |""".stripMargin,
    hint = hint
  )
}
