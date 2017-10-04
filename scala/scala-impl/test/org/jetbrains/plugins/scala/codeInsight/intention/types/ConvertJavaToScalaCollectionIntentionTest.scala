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

  def testIntentionIsAvailable_Iterable() {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list: java.lang.Iterable = new java.util.ArrayList[String]()
        |  val scalaList = lis<caret>t
        |}
      """
    )
  }

  def testIntentionIsAvailable_Collection() {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val list: java.util.Collection[String] = new java.util.ArrayList[String]()
        |  val scalaList = lis<caret>t
        |}
      """
    )
  }

  def testIntentionIsAvailable_Iterator() {
    checkIntentionIsAvailable(
      """
        |class UsesJavaCollections {
        |  val iter = new java.util.ArrayList[String]().itera<caret>tor
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
        |
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]()
        |}
      """
    val resultText =
      """
        |import java.util
        |
        |import scala.collection.JavaConverters._
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]().asScala
        |}
      """

    doTest(text, resultText)
  }
}