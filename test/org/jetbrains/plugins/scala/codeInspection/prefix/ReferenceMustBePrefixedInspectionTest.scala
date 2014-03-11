package org.jetbrains.plugins.scala
package codeInspection.prefix

import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.{ReferenceMustBePrefixedInspection, AddPrefixFix}

/**
 * Nikolay.Tropin
 * 2/25/14
 */
class ReferenceMustBePrefixedInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def annotation: String = ReferenceMustBePrefixedInspection.displayName
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ReferenceMustBePrefixedInspection]

  def testFix(text: String, result: String): Unit = testFix(text, result, AddPrefixFix.hint)

  def doTest(selected: String, text: String, result: String) = {
    check(selected)
    testFix(text, result)
  }

  def testType() = doTest (
    s"""import java.util.List
        |object AAA {
        |  val list: ${START}List$END[Int] = null
        |}""",

    s"""import java.util.List
        |object AAA {
        |  val list: ${CARET_MARKER}List[Int] = null
        |}""",

    """import java.util
       |import java.util.List
       |object AAA {
       |  val list: util.List[Int] = null
       |}"""
  )


  def testExtends() = doTest(
    s"""import scala.collection.mutable.Seq
        |object AAA extends ${START}Seq$END[Int]""",

    s"""import scala.collection.mutable.Seq
        |object AAA extends ${CARET_MARKER}Seq[Int]""",

    """import scala.collection.mutable
       |import scala.collection.mutable.Seq
       |object AAA extends mutable.Seq[Int]"""
  )

  def testApply() = doTest (
    s"""import scala.collection.mutable.Seq
        |object AAA {
        |  val s = ${START}Seq$END(0, 1)
        |}""",

    s"""import scala.collection.mutable.Seq
        |object AAA {
        |  val s = ${CARET_MARKER}Seq(0, 1)
        |}""",

    """import scala.collection.mutable
      |import scala.collection.mutable.Seq
      |object AAA {
      |  val s = mutable.Seq(0, 1)
      |}"""
  )

  def testUnapply() = doTest (
    s"""import scala.collection.mutable.HashMap
       |object AAA {
       |  Map(1 -> "a") match {
       |    case hm: ${START}HashMap$END =>
       |  }
       |}""",

    s"""import scala.collection.mutable.HashMap
       |object AAA {
       |  Map(1 -> "a") match {
       |    case hm: ${CARET_MARKER}HashMap =>
       |  }
       |}""",

    """import scala.collection.mutable
      |import scala.collection.mutable.HashMap
      |object AAA {
      |  Map(1 -> "a") match {
      |    case hm: mutable.HashMap =>
      |  }
      |}"""
  )

  def testHaveImport() = doTest(
    s"""import scala.collection.mutable.HashMap
       |import scala.collection.mutable
       |object AAA {
       |  val hm: ${START}HashMap$END = null
       |}""",

    s"""import scala.collection.mutable.HashMap
       |import scala.collection.mutable
       |object AAA {
       |  val hm: ${CARET_MARKER}HashMap = null
       |}""",

    """import scala.collection.mutable.HashMap
      |import scala.collection.mutable
      |object AAA {
      |  val hm: mutable.HashMap = null
      |}"""
  )
}
