package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ConvertJavaToScalaCollectionIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = ConvertJavaToScalaCollectionIntention.getFamilyName


  def testIntentionIsAvailable() {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList<caret>[String]()
        |}
      """)
  }

  def testIntentionIsAvailable2() {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList[String]()
        |  val scalaList = lis<caret>t
        |}
      """
    )
  }

  def testIntentionIsNotAvailable() {
    checkIntentionIsNotAvailable(
      """
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList<caret>[String]().asScala
        |}
      """)
  }

  def testIntentionAction_Simple() {
    val text =
      """
        |class UsesJavaCollections {
        |  val list = new java.util.HashMap<caret>[String, Int]()
        |}
      """
    val resultText =
      """
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new java.util.HashMap<caret>[String, Int]().asScala
        |}
      """

    doTest(text, resultText)
  }

  def testIntentionAction_Import_Already_Exists() {
    val text =
      """
        |import java.util
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]()
        |}
      """
    val resultText =
      """
        |import java.util
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]().asScala
        |}
      """

    doTest(text, resultText)
  }
}