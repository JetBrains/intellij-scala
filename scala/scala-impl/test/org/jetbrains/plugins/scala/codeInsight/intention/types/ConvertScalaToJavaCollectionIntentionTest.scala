package org.jetbrains.plugins.scala
package codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ConvertScalaToJavaCollectionIntentionTest extends ScalaIntentionTestBase {

  def familyName: String = ConvertScalaToJavaCollectionIntention.getFamilyName


  def testIntentionIsAvailable() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val list = N<caret>il
        |}
      """)
  }

  def testIntentionIsAvailable2() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val list = List(1,2,3)
        |  val javaList = lis<caret>t
        |}
      """
    )
  }

  def testIntentionIsAvailable_MutableMap() {
    checkIntentionIsAvailable(
      """
        |import scala.collection.mutable
        |
        |class UsesScalaCollections {
        |  val map = mutable.Map(1 -> "1")
        |  val javaMap = ma<caret>p
        |}
      """
    )
  }

  def testIntentionIsAvailable_Seq() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val seq = Seq("1")
        |  val javaList = se<caret>q
        |}
      """
    )
  }

  def testIntentionIsAvailable_Set() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val set = Set("1")
        |  val javaSet = se<caret>t
        |}
      """
    )
  }

  def testIntentionIsAvailable_Iterator() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val iter = Iterator(1)
        |  val javaIter = it<caret>er
        |}
      """
    )
  }

  def testIntentionIsAvailable_Iterable() {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val iter = Iterable(1)
        |  val javaIter = it<caret>er
        |}
      """
    )
  }

  def testIntentionIsNotAvailable() {
    checkIntentionIsNotAvailable(
      """
        |import scala.collection.JavaConverters._
        |
        |class UsesScalaCollections {
        |  val list = List<caret>(1,2,3).asJava
        |}
      """)
  }

  def testIntentionIsNotAvailable2() {
    checkIntentionIsNotAvailable(
      """
        |import scala.collection.JavaConverters._
        |
        |class UsesScalaCollections {
        |  val iter = Iterable(1)
        |  val javaIter = iter.as<caret>Java
        |}
      """)
  }

  def testIntentionAction_Simple() {
    val text =
      """
        |import scala.collection.immutable
        |
        |class UsesScalaCollections {
        |  val map = immutable.HashMap<caret>("1" -> 1)
        |}
      """
    val resultText =
      """
        |import scala.collection.JavaConverters._
        |import scala.collection.immutable
        |
        |class UsesScalaCollections {
        |  val map = immutable.HashMap<caret>("1" -> 1).asJava
        |}
      """

    doTest(text, resultText)
  }

  def testIntentionAction_Import_Already_Exists() {
    val text =
      """
        |import scala.collection.mutable
        |import scala.collection.JavaConverters._
        |
        |class UsesScalaCollections {
        |  val map = mutable.HashMap<caret>(1 -> "1")
        |}
      """
    val resultText =
      """
        |import scala.collection.mutable
        |import scala.collection.JavaConverters._
        |
        |class UsesScalaCollections {
        |  val map = mutable.HashMap<caret>(1 -> "1").asJava
        |}
      """

    doTest(text, resultText)
  }
}