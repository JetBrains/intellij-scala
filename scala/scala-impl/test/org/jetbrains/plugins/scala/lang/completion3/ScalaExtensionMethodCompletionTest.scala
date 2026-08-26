package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.{createPresentation, hasItemText}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture.lookupItemsDebugText
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaExtensionMethodCompletionTest extends ScalaCompletionTestBase {

  @Test
  def testSimpleExtension(): Unit = doCompletionTest(
    s"""object Test {
       |  extension (s: String)
       |    def digits: Seq[Char] = s.filter(_.isDigit)
       |
       |  "foo123".di$CARET
       |}""".stripMargin,
    s"""object Test {
       |  extension (s: String)
       |    def digits: Seq[Char] = s.filter(_.isDigit)
       |
       |  "foo123".digits
       |}""".stripMargin,
    item = "digits"
  )

  @Test
  def testExtensionFromGiven(): Unit = doCompletionTest(
    s"""object math3:
       |  trait Ord[T]
       |
       |  trait Numeric[T] extends Ord[T]:
       |    extension (x: Int) def numeric: T = ???
       |
       |object Test3:
       |  import math3.Numeric
       |
       |  def to[T: Numeric](x: Int): T =
       |    x.num$CARET""".stripMargin,
    """object math3:
      |  trait Ord[T]
      |
      |  trait Numeric[T] extends Ord[T]:
      |    extension (x: Int) def numeric: T = ???
      |
      |object Test3:
      |  import math3.Numeric
      |
      |  def to[T: Numeric](x: Int): T =
      |    x.numeric""".stripMargin,
    item = "numeric"
  )

  @Test
  def testFromImplicitScope(): Unit = doCompletionTest(
    s"""class MyList[+T]
       |
       |object MyList:
       |  def apply[A](a: A*): MyList[A] = ???
       |
       |  extension [T](xs: MyList[MyList[T]])
       |    def flatten: MyList[T] = ???
       |
       |object Test {
       |  MyList(MyList(1, 2), MyList(3, 4)).fl$CARET
       |}""".stripMargin,
    """class MyList[+T]
      |
      |object MyList:
      |  def apply[A](a: A*): MyList[A] = ???
      |
      |  extension [T](xs: MyList[MyList[T]])
      |    def flatten: MyList[T] = ???
      |
      |object Test {
      |  MyList(MyList(1, 2), MyList(3, 4)).flatten
      |}""".stripMargin,
  "flatten")

  @Test
  def testExtensionWithConversionApplicableReceiverIsCompleted(): Unit = doCompletionTest(
    s"""class OriginalReceiver
       |class ConvertedReceiver
       |
       |given Conversion[OriginalReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def convertedExtension: Int = 1
       |
       |(null: OriginalReceiver).convertedEx$CARET""".stripMargin,
    """class OriginalReceiver
      |class ConvertedReceiver
      |
      |given Conversion[OriginalReceiver, ConvertedReceiver] = null
      |
      |extension (receiver: ConvertedReceiver)
      |  def convertedExtension: Int = 1
      |
      |(null: OriginalReceiver).convertedExtension""".stripMargin,
    item = "convertedExtension"
  )

  @Test
  def testExtensionWithUnrelatedReceiverIsNotCompleted(): Unit = checkNoBasicCompletion(
    s"""class ActualReceiver
       |class UnrelatedReceiver
       |
       |extension (receiver: UnrelatedReceiver)
       |  def unrelatedExtension: Int = 1
       |
       |(null: ActualReceiver).unrelatedEx$CARET""".stripMargin,
    item = "unrelatedExtension"
  )

  @Test
  def testExtensionWithAmbiguousReceiverConversionIsNotCompleted(): Unit = checkNoBasicCompletion(
    s"""class OriginalReceiver
       |class ConvertedReceiver
       |
       |given firstConversion: Conversion[OriginalReceiver, ConvertedReceiver] = null
       |given secondConversion: Conversion[OriginalReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def ambiguousExtension: Int = 1
       |
       |(null: OriginalReceiver).ambiguousEx$CARET""".stripMargin,
    item = "ambiguousExtension"
  )

  @Test
  def testExtensionRequiringChainedReceiverConversionsIsNotCompleted(): Unit = checkNoBasicCompletion(
    s"""class OriginalReceiver
       |class IntermediateReceiver
       |class FinalReceiver
       |
       |given Conversion[OriginalReceiver, IntermediateReceiver] = null
       |given Conversion[IntermediateReceiver, FinalReceiver] = null
       |
       |extension (receiver: FinalReceiver)
       |  def chainedExtension: Int = 1
       |
       |(null: OriginalReceiver).chainedEx$CARET""".stripMargin,
    item = "chainedExtension"
  )

  @Test
  def testImportSelectorShowsReceiverForSingleExtensionMethod(): Unit = {
    val (_, items) = activeLookupWithItems(
      s"""class User
         |
         |object Definitions:
         |  extension (target: User) def present(suffix: String): String = "user"
         |
         |object Usage:
         |  import Definitions.pre$CARET
         |""".stripMargin
    )
    val presentItems = items.filter(_.getLookupString == "present").toSeq

    assertEquals(1, presentItems.size)
    assertTrue(
      s"""Unexpected completion presentation:
         |${lookupItemsDebugText(presentItems)}""".stripMargin,
      hasItemText(presentItems.head, "present")(
        itemTextBold = true,
        tailText = "(suffix: String) for User in Definitions",
        typeText = "String"
      )
    )
  }

  @Test
  def testImportSelectorShowsGenericReceiverForSingleExtensionMethod(): Unit = {
    val (_, items) = activeLookupWithItems(
      s"""class Box[T]
         |
         |object Definitions:
         |  extension [T](target: Box[T]) def present[A](suffix: A): T = ???
         |
         |object Usage:
         |  import Definitions.pre$CARET
         |""".stripMargin
    )
    val presentItems = items.filter(_.getLookupString == "present").toSeq

    assertEquals(1, presentItems.size)
    assertEquals("[A](suffix: A) for Box[T] in Definitions", createPresentation(presentItems.head).getTailText)
  }

  @Test
  def testImportSelectorShowsExtensionMethodOverloadsSeparately(): Unit = {
    val fileText =
      s"""class User
         |class Project
         |class Domain
         |
         |object Definitions:
         |  extension (target: User) def present: String = "user"
         |  extension (target: Project) def present: String = "project"
         |  extension (target: Domain) def present: String = "domain"
         |
         |object Usage:
         |  import Definitions.pre$CARET
         |""".stripMargin

    val (_, items) = activeLookupWithItems(fileText)
    val presentItems = items.filter(_.getLookupString == "present").toSeq

    assertEquals(
      s"""Expected one import completion item per extension declaration.
         |All lookup items:
         |${lookupItemsDebugText(items)}""".stripMargin,
      3,
      presentItems.size
    )
    assertTrue(
      s"""Missing User extension completion presentation:
         |${lookupItemsDebugText(presentItems)}""".stripMargin,
      presentItems.exists(hasItemText(_, "present")(
        itemTextBold = true,
        tailText = " for User in Definitions",
        typeText = "String"
      ))
    )
    assertTrue(presentItems.exists(hasItemText(_, "present")(
      itemTextBold = true,
      tailText = " for Project in Definitions",
      typeText = "String"
    )))
    assertTrue(presentItems.exists(hasItemText(_, "present")(
      itemTextBold = true,
      tailText = " for Domain in Definitions",
      typeText = "String"
    )))

    doCompletionTest(
      fileText,
      s"""class User
         |class Project
         |class Domain
         |
         |object Definitions:
         |  extension (target: User) def present: String = "user"
         |  extension (target: Project) def present: String = "project"
         |  extension (target: Domain) def present: String = "domain"
         |
         |object Usage:
         |  import Definitions.present
         |""".stripMargin,
      item = "present"
    )
  }

  @Test
  def testImportSelectorShowsDistinctReturnTypesForExtensionMethodOverloads(): Unit = {
    val (_, items) = activeLookupWithItems(
      s"""class User
         |class Project
         |
         |object Definitions:
         |  extension (target: User) def present: String = "user"
         |  extension (target: Project) def present: Int = 42
         |
         |object Usage:
         |  import Definitions.pre$CARET
         |""".stripMargin
    )
    val presentItems = items.filter(_.getLookupString == "present").toSeq

    assertEquals(2, presentItems.size)
    assertTrue(
      s"""Missing User extension completion presentation:
         |${lookupItemsDebugText(presentItems)}""".stripMargin,
      presentItems.exists(hasItemText(_, "present")(
        itemTextBold = true,
        tailText = " for User in Definitions",
        typeText = "String"
      ))
    )
    assertTrue(presentItems.exists(hasItemText(_, "present")(
      itemTextBold = true,
      tailText = " for Project in Definitions",
      typeText = "Int"
    )))
  }

  @Test
  def testImportSelectorKeepsOrdinaryMethodsSeparateFromExtensionMethods(): Unit = {
    val (_, items) = activeLookupWithItems(
      s"""class User
         |class Project
         |
         |object Definitions:
         |  extension (target: User) def present: String = "user"
         |  extension (target: Project) def present: String = "project"
         |  def present: Boolean = true
         |
         |object Usage:
         |  import Definitions.pre$CARET
         |""".stripMargin
    )
    val presentItems = items.filter(_.getLookupString == "present").toSeq

    assertEquals(
      s"""Every extension declaration and the ordinary method must stay separate.
         |All lookup items:
         |${lookupItemsDebugText(items)}""".stripMargin,
      3,
      presentItems.size
    )
    assertTrue(
      s"""Missing User extension completion presentation:
         |${lookupItemsDebugText(presentItems)}""".stripMargin,
      presentItems.exists(hasItemText(_, "present")(
        itemTextBold = true,
        tailText = " for User in Definitions",
        typeText = "String"
      ))
    )
    assertTrue(presentItems.exists(hasItemText(_, "present")(
      itemTextBold = true,
      tailText = " for Project in Definitions",
      typeText = "String"
    )))
    assertTrue(presentItems.exists(item => createPresentation(item).getTypeText == "Boolean"))
  }
}
