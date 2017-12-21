package org.jetbrains.plugins.scala
package codeInspection.prefix

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.ReferenceMustBePrefixedInspection
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
  * Nikolay.Tropin
  * 2/25/14
  */
class ReferenceMustBePrefixedInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
  import ReferenceMustBePrefixedInspection._

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ReferenceMustBePrefixedInspection]

  override protected val description: String = DESCRIPTION

  def testType(): Unit = testQuickFix(
    text =
      s"""import java.util.List
         |
         |object AAA {
         |  val list: ${START}List$END[Int] = null
         |}
       """.stripMargin,
    expected =
      """import java.util
        |import java.util.List
        |
        |object AAA {
        |  val list: util.List[Int] = null
        |}
      """.stripMargin
  )


  def testExtends(): Unit = testQuickFix(
    text =
      s"""import scala.collection.mutable.Seq
         |
         |object AAA extends ${START}Seq$END[Int]
         """.stripMargin,
    expected =
      """import scala.collection.mutable
        |import scala.collection.mutable.Seq
        |
        |object AAA extends mutable.Seq[Int]
      """.stripMargin
  )

  def testApply(): Unit = testQuickFix(
    text =
      s"""import scala.collection.mutable.Seq
         |
       |object AAA {
         |  val s = ${START}Seq$END(0, 1)
         |}
       """.stripMargin,
    expected =
      """import scala.collection.mutable
        |import scala.collection.mutable.Seq
        |
        |object AAA {
        |  val s = mutable.Seq(0, 1)
        |}
      """.stripMargin
  )

  def testUnapply(): Unit = testQuickFix(
    text =
      s"""import scala.collection.mutable.HashMap
         |
       |object AAA {
         |  Map(1 -> "a") match {
         |    case hm: ${START}HashMap$END =>
         |  }
         |}
       """.stripMargin,
    expected =
      """import scala.collection.mutable
        |import scala.collection.mutable.HashMap
        |
        |object AAA {
        |  Map(1 -> "a") match {
        |    case hm: mutable.HashMap =>
        |  }
        |}
      """.stripMargin
  )

  def testHaveImport(): Unit = testQuickFix(
    text =
      s"""import scala.collection.mutable.HashMap
         |import scala.collection.mutable
         |
         |object AAA {
         |  val hm: ${START}HashMap$END = null
         |}
       """.stripMargin,
    expected =
      """import scala.collection.mutable.HashMap
        |import scala.collection.mutable
        |
        |object AAA {
        |  val hm: mutable.HashMap = null
        |}
      """.stripMargin
  )

  def testInnerClass(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    val patterns = settings.getImportsWithPrefix
    settings.setImportsWithPrefix(patterns :+ "bar.Outer._")

    testQuickFix(
      text =
        s"""package bar
           |
           |object Outer {
           | class Inner
           |}
           |
           |import Outer.Inner
           |
           |object Test {
           |  val i: ${START}Inner$END = null
           |}
           """.stripMargin,
      expected =
        """
          |package bar
          |
          |object Outer {
          | class Inner
          |}
          |
          |import Outer.Inner
          |
          |object Test {
          |  val i: Outer.Inner = null
          |}
        """.stripMargin
    )

    settings.setImportsWithPrefix(patterns)
  }

  def testInnerClassFromContaining(): Unit = checkTextHasNoErrors(
    """
      |package bar
      |
      |object Outer {
      |  class Inner
      |
      |  val i: Inner = null
      |}
    """.stripMargin
  )

  private def testQuickFix(text: String, expected: String): Unit = {
    testQuickFix(text, expected, AddPrefixQuickFix.HINT)
  }
}
