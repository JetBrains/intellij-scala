package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

class ImportNamedTypeArgumentsFeatureFlagQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected val description: String =
    ScalaBundle.message("named.type.arguments.require.language.experimental.named.type.arguments")

  private val hint = ScalaInspectionBundle.message(
    "import.feature.flag.for.language.feature",
    ScalaInspectionBundle.message("language.feature.named.type.argument")
  )

  def testMethodInvocation(): Unit = testQuickFix(
    text =
      s"""
         |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
         |
         |val xs = construct[${START}Coll$END = List, Elem = Int](1, 2, 3)
         |""".stripMargin,
    expected =
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
        |
        |val xs = construct[Coll = List, Elem = Int](1, 2, 3)
        |""".stripMargin,
    hint = hint
  )

  def testGenericCallWithoutValueArgs(): Unit = testQuickFix(
    text =
      s"""
         |def make[Elem, Coll[_]]: Coll[Elem] = ???
         |
         |val xs: List[Int] = make[${START}Coll$END = List, Elem = Int]
         |""".stripMargin,
    expected =
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def make[Elem, Coll[_]]: Coll[Elem] = ???
        |
        |val xs: List[Int] = make[Coll = List, Elem = Int]
        |""".stripMargin,
    hint = hint
  )

  def testInvocationInInnerScope_ImportInsertedAtFileScope(): Unit = testQuickFix(
    text =
      s"""
         |object Test {
         |  def outer(): Unit = {
         |    def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
         |    val xs = construct[${START}Coll$END = List, Elem = Int](1, 2, 3)
         |  }
         |}
         |""".stripMargin,
    expected =
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |object Test {
        |  def outer(): Unit = {
        |    def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
        |    val xs = construct[Coll = List, Elem = Int](1, 2, 3)
        |  }
        |}
        |""".stripMargin,
    hint = hint
  )
}
