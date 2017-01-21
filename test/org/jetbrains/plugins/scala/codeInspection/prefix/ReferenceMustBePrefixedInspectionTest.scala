package org.jetbrains.plugins.scala
package codeInspection.prefix

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.{AddPrefixFix, ReferenceMustBePrefixedInspection}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
  * Nikolay.Tropin
  * 2/25/14
  */
class ReferenceMustBePrefixedInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ReferenceMustBePrefixedInspection]

  override protected val description: String =
    ReferenceMustBePrefixedInspection.displayName

  private def doTest(selected: String, text: String, result: String) = {
    checkTextHasError(selected)
    testQuickFix(text, result, AddPrefixFix.hint)
  }

  def testType() = doTest(
    s"""import java.util.List
       |
        |object AAA {
       |  val list: ${START}List$END[Int] = null
       |}""",

    s"""import java.util.List
       |
        |object AAA {
       |  val list: ${CARET_MARKER}List[Int] = null
       |}""",

    """import java.util
      |import java.util.List
      |
      |object AAA {
      |  val list: util.List[Int] = null
      |}"""
  )


  def testExtends() = doTest(
    s"""import scala.collection.mutable.Seq
       |
        |object AAA extends ${START}Seq$END[Int]""",

    s"""import scala.collection.mutable.Seq
       |
        |object AAA extends ${CARET_MARKER}Seq[Int]""",

    """import scala.collection.mutable
      |import scala.collection.mutable.Seq
      |
      |object AAA extends mutable.Seq[Int]"""
  )

  def testApply() = doTest(
    s"""import scala.collection.mutable.Seq
       |
        |object AAA {
       |  val s = ${START}Seq$END(0, 1)
       |}""",

    s"""import scala.collection.mutable.Seq
       |
        |object AAA {
       |  val s = ${CARET_MARKER}Seq(0, 1)
       |}""",

    """import scala.collection.mutable
      |import scala.collection.mutable.Seq
      |
      |object AAA {
      |  val s = mutable.Seq(0, 1)
      |}"""
  )

  def testUnapply() = doTest(
    s"""import scala.collection.mutable.HashMap
       |
       |object AAA {
       |  Map(1 -> "a") match {
       |    case hm: ${START}HashMap$END =>
       |  }
       |}""",

    s"""import scala.collection.mutable.HashMap
       |
       |object AAA {
       |  Map(1 -> "a") match {
       |    case hm: ${CARET_MARKER}HashMap =>
       |  }
       |}""",

    """import scala.collection.mutable
      |import scala.collection.mutable.HashMap
      |
      |object AAA {
      |  Map(1 -> "a") match {
      |    case hm: mutable.HashMap =>
      |  }
      |}"""
  )

  def testHaveImport() = doTest(
    s"""import scala.collection.mutable.HashMap
       |import scala.collection.mutable
       |
       |object AAA {
       |  val hm: ${START}HashMap$END = null
       |}""",

    s"""import scala.collection.mutable.HashMap
       |import scala.collection.mutable
       |
       |object AAA {
       |  val hm: ${CARET_MARKER}HashMap = null
       |}""",

    """import scala.collection.mutable.HashMap
      |import scala.collection.mutable
      |
      |object AAA {
      |  val hm: mutable.HashMap = null
      |}"""
  )

  def testInnerClass(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    val patterns = settings.getImportsWithPrefix
    settings.setImportsWithPrefix(patterns :+ "bar.Outer._")
    doTest(
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
         |}""",
      s"""package bar
         |
         |object Outer {
         | class Inner
         |}
         |
         |import Outer.Inner
         |
         |object Test {
         |  val i: ${CARET_MARKER}Inner = null
         |}""",
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
        |}"""
    )
    settings.setImportsWithPrefix(patterns)
  }

  def testInnerClassFromContaining(): Unit = {
    checkTextHasNoErrors(
      """
        |package bar
        |
        |object Outer {
        |  class Inner
        |
        |  val i: Inner = null
        |}""")
  }
}
