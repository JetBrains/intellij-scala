package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_13}
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class ConvertJavaToScalaCollectionIntentionBaseTest(converters: String)
  extends ScalaIntentionTestBase {

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
      s"""
        |import $converters
        |
        |class UsesJavaCollections {
        |  val list = new java.util.HashMap<caret>[String, Int]().asScala
        |}
      """

    doTest(text, resultText)
  }

  def testIntentionAction_Import_Already_Exists() {
    val text =
      s"""
        |import java.util
        |
        |import $converters
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]()
        |}
      """
    val resultText =
      s"""
        |import java.util
        |
        |import $converters
        |
        |class UsesJavaCollections {
        |  val list = new util.HashMap<caret>[String, Int]().asScala
        |}
      """

    doTest(text, resultText)
  }


}

class ConvertJavaToScalaCollectionIntentionTest
  extends ConvertJavaToScalaCollectionIntentionBaseTest("scala.collection.JavaConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version < Scala_2_13
}

class ConvertJavaToScalaCollectionIntention_2_13Test
  extends ConvertJavaToScalaCollectionIntentionBaseTest("scala.jdk.CollectionConverters._") {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13
}
