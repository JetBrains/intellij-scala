package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ImplicitConvertJavaToScalaCollectionIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = ImplicitConvertJavaToScalaCollectionIntention.getFamilyName


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
        |import scala.collection.JavaConversions._
        |
        |class UsesJavaCollections {
        |  val list = new java.util.ArrayList<caret>[String]()
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
        |import scala.collection.JavaConversions._
        |
        |class UsesJavaCollections {
        |  val list = new java.util.HashMap<caret>[String, Int]()
        |}
      """

    doTest(text, resultText)
  }
}