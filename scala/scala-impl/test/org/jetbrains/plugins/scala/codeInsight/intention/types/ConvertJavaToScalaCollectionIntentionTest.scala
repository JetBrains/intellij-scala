package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ConvertJavaToScalaCollectionIntentionBaseTest(converters: String)
  extends ScalaIntentionTestBase {

  override def familyName: String = ConvertJavaToScalaCollectionIntention.getFamilyName

  private def wrapInClassWithValueDefinition(rhs: String, addImport: Boolean = false): String =
    s"""${if (addImport) "import " + converters + "\n" else ""}import scala.language.postfixOps
       |
       |class UsesJavaCollection {
       |  val list = $rhs
       |}
       |""".stripMargin

  def testNew_generic_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"new java.util.ArrayList$CARET[String]()"),
    wrapInClassWithValueDefinition(s"new java.util.ArrayList$CARET[String]().asScala", addImport = true)
  )

  def testNew_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"new java.ut${CARET}il.ArrayList()"),
    wrapInClassWithValueDefinition(s"new java.ut${CARET}il.ArrayList().asScala", addImport = true)
  )

  def testNew_generic_withoutParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"new java.util.ArrayList$CARET[String]"),
    wrapInClassWithValueDefinition(s"new java.util.ArrayList$CARET[String].asScala", addImport = true)
  )

  def testNew_withoutParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"new java.ut${CARET}il.ArrayList"),
    wrapInClassWithValueDefinition(s"new java.ut${CARET}il.ArrayList.asScala", addImport = true)
  )

  def testCall_generic_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList[String]()"),
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList[String]().asScala", addImport = true)
  )

  def testCall_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList()"),
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList().asScala", addImport = true)
  )

  def testCall_generic_withoutParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList[String]"),
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList[String].asScala", addImport = true)
  )

  def testCall_withoutParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList"),
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions.emptyList.asScala", addImport = true)
  )

  def testInfix_generic(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collections singletonList$CARET[Int] 1"),
    wrapInClassWithValueDefinition("(java.util.Collections singletonList[Int] 1).asScala", addImport = true)
  )

  def testInfix_generic_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions singletonList[Boolean] (true)"),
    wrapInClassWithValueDefinition("(java.util.Collections singletonList[Boolean] (true)).asScala", addImport = true)
  )

  def testInfix(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collect${CARET}ions singletonList 1"),
    wrapInClassWithValueDefinition("(java.util.Collections singletonList 1).asScala", addImport = true)
  )

  def testInfix_withParens(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collections emptyList$CARET ()"),
    wrapInClassWithValueDefinition("(java.util.Collections emptyList()).asScala", addImport = true)
  )

  def testPostfix(): Unit = doTest(
    wrapInClassWithValueDefinition(s"java.util.Collec${CARET}tions emptyList"),
    wrapInClassWithValueDefinition("(java.util.Collections emptyList).asScala", addImport = true)
  )

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
      s"""
         |import $converters
         |
         |class UsesJavaCollections {
         |  val list = new java.util.ArrayList$CARET[String]().asScala
         |}
      """.stripMargin)
  }

  def testIntentionIsNotAvailable2(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |import $converters
         |
         |class UsesJavaCollections {
         |  val list = (java.util.Collect${CARET}ions singletonList[Boolean] (true)).asScala
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
