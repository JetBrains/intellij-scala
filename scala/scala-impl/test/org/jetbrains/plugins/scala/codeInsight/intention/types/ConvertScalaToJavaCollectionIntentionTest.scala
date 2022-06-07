package org.jetbrains.plugins.scala
package codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class ConvertScalaToJavaCollectionIntentionBaseTest(converters: String) extends ScalaIntentionTestBase {

  override def familyName: String = ConvertScalaToJavaCollectionIntention.getFamilyName

  def testIntentionIsAvailable(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val list = N<caret>il
        |}
      """.stripMargin)
  }

  def testIntentionIsAvailable2(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val list = List(1,2,3)
        |  val javaList = lis<caret>t
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_MutableMap(): Unit = {
    checkIntentionIsAvailable(
      """
        |import scala.collection.mutable
        |
        |class UsesScalaCollections {
        |  val map = mutable.Map(1 -> "1")
        |  val javaMap = ma<caret>p
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Seq(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val seq = Seq("1")
        |  val javaList = se<caret>q
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Set(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val set = Set("1")
        |  val javaSet = se<caret>t
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Iterator(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val iter = Iterator(1)
        |  val javaIter = it<caret>er
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Iterable(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesScalaCollections {
        |  val iter = Iterable(1)
        |  val javaIter = it<caret>er
        |}
      """.stripMargin
    )
  }

  def testIntentionIsNotAvailable(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |import $converters
         |
         |class UsesScalaCollections {
         |  val list = List<caret>(1,2,3).asJava
         |}
      """.stripMargin)
  }

  def testIntentionIsNotAvailable2(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |import $converters
         |
         |class UsesScalaCollections {
         |  val iter = Iterable(1)
         |  val javaIter = iter.as<caret>Java
         |}
      """.stripMargin)
  }

  def testIntentionAction_Simple(): Unit = {
    val text =
      """class UsesScalaCollections {
        |  val map = Map<caret>("1" -> 1)
        |}
        |""".stripMargin
    val resultText =
      s"""import $converters
         |
         |class UsesScalaCollections {
         |  val map = Map<caret>("1" -> 1).asJava
         |}
         |""".stripMargin

    doTest(text, resultText)
  }

  def testIntentionAction_Import_Already_Exists(): Unit = {
    val text =
      s"""
         |import scala.collection.mutable
         |import $converters
         |
         |class UsesScalaCollections {
         |  val map = mutable.HashMap<caret>(1 -> "1")
         |}
         |""".stripMargin
    val resultText =
      s"""
         |import scala.collection.mutable
         |import $converters
         |
         |class UsesScalaCollections {
         |  val map = mutable.HashMap<caret>(1 -> "1").asJava
         |}
         |""".stripMargin

    doTest(text, resultText)
  }
}


class ConvertScalaToJavaCollectionIntentionTest
  extends ConvertScalaToJavaCollectionIntentionBaseTest("scala.collection.JavaConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version < LatestScalaVersions.Scala_2_13
}

class ConvertScalaToJavaCollectionIntention_2_13Test
  extends ConvertScalaToJavaCollectionIntentionBaseTest("scala.jdk.CollectionConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13
}