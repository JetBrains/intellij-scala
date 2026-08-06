package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.ScalaVersion

class ScalaBackspaceAndEnterWithBracesTestBase_Scala2 extends ScalaBackspaceAndEnterWithBracesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < ScalaVersion.Latest.Scala_3_0
}

class ScalaBackspaceAndEnterWithBracesTestBase_Scala3 extends ScalaBackspaceAndEnterWithBracesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0
}

abstract class ScalaBackspaceAndEnterWithBracesTestBase extends ScalaBackspaceAndEnterHandlerBaseTest {

  def testNestedMethodBody_Simple_WithBraces_1(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""class A {
         |  def f1 = {
         |    def f2 = {
         |      def f3 = {
         |        "f3"#
         |        #
         |        #
         |        #    #
         |      }
         |    }
         |  }
         |}""".stripMargin
    )

  def testNestedMethodBody_Simple_WithBraces_2(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""class A {
         |  def f1 = {
         |    def f2 = {
         |      def f3 = {
         |        "f3"#
         |        #
         |        #
         |  #
         |      }
         |    }
         |  }
         |}""".stripMargin
    )

  def testAfterMiddleCaseClause_WithCode_1(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>
         |    111#
         |    #
         |    #
         |    #   #
         |  case _ =>
         |}""".stripMargin
    )

  def testAfterMiddleCaseClause_WithCode_2(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>
         |    111#
         |    #
         |    #
         |  #
         |  case _ =>
         |}""".stripMargin
    )

  def testAfterLastCaseClause_WithCode_1(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>
         |    111#
         |    #
         |    #
         |    #   #
         |}""".stripMargin
    )

  def testAfterLastCaseClause_WithCode_2(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>
         |    111#
         |    #
         |    #
         |  #
         |}""".stripMargin
    )

  def testAfterMiddleCaseClause_WithoutCode_1(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>#
         |    #
         |    #
         |    #
         |    #   #
         |  case _ =>
         |}""".stripMargin
    )

  def testAfterMiddleCaseClause_WithoutCode_2(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>#
         |    #
         |    #
         |    #
         |#
         |  case _ =>
         |}""".stripMargin
    )

  def testAfterLastCaseClause_WithoutCode_1(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>#
         |    #
         |    #
         |    #
         |    #   #
         |}""".stripMargin
    )

  def testAfterLastCaseClause_WithoutCode_2(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      s"""Option(42) match {
         |  case Some(1) =>#
         |    #
         |    #
         |#
         |}""".stripMargin
    )

  def testBeforeFirstCaseClause(): Unit =
    doSequentialBackspaceAndEnterTest_InAllWrapperContexts(
      1,
      """Option(42) match {#
        |  #
        |  #
        |  #   #
        |  case Some(1) =>
        |}
        |""".stripMargin
    )
}
