package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ConvertJavaToScalaCollectionIntentionBaseTest(converters: String)
  extends ScalaIntentionTestBase {

  override def familyName: String = ConvertJavaToScalaCollectionIntention.getFamilyName


  def testIntentionIsAvailable(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList<caret>[String]()
        |}
      """.stripMargin)
  }

  def testIntentionIsAvailable_Iterable(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list: java.lang.Iterable = new java.util.ArrayList[String]()
        |  val scalaList = lis<caret>t
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Collection(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list: java.util.Collection[String] = new java.util.ArrayList[String]()
        |  val scalaList = lis<caret>t
        |}
      """.stripMargin
    )
  }

  def testIntentionIsAvailable_Iterator(): Unit = {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val iter = new java.util.ArrayList[String]().itera<caret>tor
        |}
      """.stripMargin
    )
  }

  def testIntentionIsNotAvailable(): Unit = {
    checkIntentionIsNotAvailable(
      """
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList<caret>[String]().asScala
        |}
      """.stripMargin)
  }

  def testIntentionAction_Simple(): Unit = {
    val text =
      """class UsesJavaCollections {
        |  val list = new java.util.HashMap<caret>[String, Int]()
        |}
        |""".stripMargin
    val resultText =
      s"""import $converters
         |
         |class UsesJavaCollections {
         |  val list = new java.util.HashMap<caret>[String, Int]().asScala
         |}
         |""".stripMargin

    doTest(text, resultText)
  }

  def testIntentionAction_Import_Already_Exists(): Unit = {
    val text =
      s"""
         |import java.util
         |import $converters
         |
         |class UsesJavaCollections {
         |  val list = new util.HashMap<caret>[String, Int]()
         |}
         |""".stripMargin
    val resultText =
      s"""
         |import java.util
         |import $converters
         |
         |class UsesJavaCollections {
         |  val list = new util.HashMap<caret>[String, Int]().asScala
         |}
         |""".stripMargin

    doTest(text, resultText)
  }


}

class ConvertJavaToScalaCollectionIntentionTest
  extends ConvertJavaToScalaCollectionIntentionBaseTest("scala.collection.JavaConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version < LatestScalaVersions.Scala_2_13
}

class ConvertJavaToScalaCollectionIntention_2_13Test
  extends ConvertJavaToScalaCollectionIntentionBaseTest("scala.jdk.CollectionConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13
}
