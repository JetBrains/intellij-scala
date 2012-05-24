package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.junit.Assert
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}

/**
 * User: Alefas
 * Date: 27.03.12
 */

class ScalaClassNameCompletionTest extends ScalaCompletionTestBase {
  def testClassNameRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.CLASS_NAME)

    val resultText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BLLLL").get, '\t')
    checkResultByText(resultText)
  }

  def testExpressionSameName() {
    val fileText =
      """
        |import collection.immutable.HashSet
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.CLASS_NAME)

    val resultText =
      """
        |import collection.immutable.HashSet
        |import collection.mutable
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  mutable.HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScObject if c.qualifiedName == "scala.collection.mutable.HashSet" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }

  def testClassSameName() {
    val fileText =
      """
        |import collection.immutable.HashSet
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  val y: HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.CLASS_NAME)

    val resultText =
      """
        |import collection.immutable.HashSet
        |import collection.mutable
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  val y: mutable.HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScClass if c.qualifiedName == "scala.collection.mutable.HashSet" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }

  def testSmartJoining() {
    val fileText =
      """
        |import collection.mutable.{Builder, Queue}
        |import scala.collection.immutable.HashMap
        |import collection.mutable.ArrayBuffer
        |
        |object Sandbox extends App {
        |  val m: ListM<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.CLASS_NAME)

    val resultText =
      """
        |import collection.mutable.{ListMap, Builder, Queue, ArrayBuffer}
        |import scala.collection.immutable.HashMap
        |
        |object Sandbox extends App {
        |  val m: ListMap
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScClass if c.qualifiedName == "scala.collection.mutable.ListMap" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }
}
