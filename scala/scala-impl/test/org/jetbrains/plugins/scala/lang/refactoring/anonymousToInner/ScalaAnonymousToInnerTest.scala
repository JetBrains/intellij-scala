package org.jetbrains.plugins.scala.lang.refactoring.anonymousToInner

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import com.intellij.ui.UiInterceptors
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.refactoring.move.anonymousToInner.ScalaAnonymousToInnerDialog
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert
import org.junit.Assert.{assertEquals, fail}

import scala.util.{Failure, Try}

class ScalaAnonymousToInnerTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override def getTestDataPath: String = TestUtils.getTestDataPath + "/refactoring/anonymousToInner/"

  def testInsideObject(): Unit = {
    val before =
      s"""object MyObject {
         |
         |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Ite${Caret}rator[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = someNum
         |      })
         |    } else
         |      None
         |}
         |""".stripMargin
    val after =
      """object MyObject {
        |
        |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
        |    if (input.hasNext) {
        |      Some(new IntIterator(input, someNum))
        |    } else
        |      None
        |
        |  private class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
        |    def hasNext = input.hasNext
        |
        |    def next = someNum
        |  }
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testInsideClass(): Unit = {
    val before =
      s"""class MyClass() {
         |
         |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Ite${Caret}rator[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = someNum
         |      })
         |    } else
         |      None
         |}
         |""".stripMargin
    val after =
      """class MyClass() {
        |
        |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
        |    if (input.hasNext) {
        |      Some(new IntIterator(input, someNum))
        |    } else
        |      None
        |
        |  private class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
        |    def hasNext = input.hasNext
        |
        |    def next = someNum
        |  }
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testInsideTrait(): Unit = {
    val before =
      s"""trait MyTrait {
         |  def parseT(input: Iterator[Int], num: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Iter${Caret}ator[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = num
         |      })
         |    } else {
         |      None
         |    }
         |}
         |""".stripMargin
    val after =
      """trait MyTrait {
        |  def parseT(input: Iterator[Int], num: Int): Option[Iterator[Int]] =
        |    if (input.hasNext) {
        |      Some(new IntIterator(input, num))
        |    } else {
        |      None
        |    }
        |
        |  private class IntIterator(input: Iterator[Int], num: Int) extends Iterator[Int] {
        |    def hasNext = input.hasNext
        |
        |    def next = num
        |  }
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testInsideEnum(): Unit = {
    val before =
      s"""enum MyEnum {
         |  case ONE, TWO, THREE
         |
         |  def parseE(input: Iterator[Int], num: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Itera${Caret}tor[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = num
         |      })
         |    } else {
         |      None
         |    }
         |}
         |""".stripMargin
    val after =
      """enum MyEnum {
        |  case ONE, TWO, THREE
        |
        |  def parseE(input: Iterator[Int], num: Int): Option[Iterator[Int]] =
        |    if (input.hasNext) {
        |      Some(new IntIterator(input, num))
        |    } else {
        |      None
        |    }
        |
        |  private class IntIterator(input: Iterator[Int], num: Int) extends Iterator[Int] {
        |    def hasNext = input.hasNext
        |
        |    def next = num
        |  }
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }


  def testTopLevel(): Unit = {
    val before =
      s"""def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
         |  if (input.hasNext) {
         |    Some(new Ite${Caret}rator[Int] {
         |      def hasNext = input.hasNext
         |
         |      def next = someNum
         |    })
         |  } else
         |    None
         |""".stripMargin
    val after =
      """def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
        |  if (input.hasNext) {
        |    Some(new IntIterator(input, someNum))
        |  } else
        |    None
        |
        |private class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
        |  def hasNext = input.hasNext
        |
        |  def next = someNum
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testNestedAnonymousClasses(): Unit = {
    val before =
      s"""def parse(input: Iterator[Byte], sliceSize: Int): Option[Iterator[Byte]] =
         |  if (input.hasNext) {
         |    Some(new Iterator[Byte] {
         |      val secondLevelIterator = new Iter${Caret}ator[Int] {
         |
         |        override def hasNext: Boolean = true
         |
         |        override def next(): Int = sliceSize
         |      }
         |
         |      def hasNext = input.hasNext
         |
         |      def next = sliceSize.toByte
         |    })
         |  } else {
         |    None
         |  }""".stripMargin
    val after =
      """def parse(input: Iterator[Byte], sliceSize: Int): Option[Iterator[Byte]] =
        |  if (input.hasNext) {
        |    Some(new Iterator[Byte] {
        |      val secondLevelIterator = new IntIterator(sliceSize)
        |
        |      def hasNext = input.hasNext
        |
        |      def next = sliceSize.toByte
        |    })
        |  } else {
        |    None
        |  }
        |
        |private class IntIterator(sliceSize: Int) extends Iterator[Int] {
        |
        |  override def hasNext: Boolean = true
        |
        |  override def next(): Int = sliceSize
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testVarInsideAnonClass(): Unit = {
    val before =
      s"""object MyClass {
         |
         |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Ite${Caret}rator[Int] {
         |        var x = 0
         |        def hasNext = input.hasNext
         |
         |        def next = someNum + x
         |      })
         |    } else
         |      None
         |}
         |""".stripMargin
    val after =
      """object MyClass {
        |
        |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
        |    if (input.hasNext) {
        |      Some(new IntIterator(input, someNum))
        |    } else
        |      None
        |
        |  private class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
        |    var x = 0
        |
        |    def hasNext = input.hasNext
        |
        |    def next = someNum + x
        |  }
        |}""".stripMargin

    val className = "IntIterator"

    doTest(before, after, className)
  }

  def testVarOutsideAnonClass(): Unit = {
    val before =
      s"""object MyClass {
         |  var myVarName1 = 1
         |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] = {
         |    var myVarName2 = 2
         |    if (input.hasNext) {
         |      Some(new Ite${Caret}rator[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = someNum + myVarName1 + myVarName2
         |      })
         |    }
         |    else None
         |  }
         |}
         |""".stripMargin

    val expectedErrorMessage =
      """Cannot perform refactoring.
        |Extraction of anonymous class with references to vars out of scope is currently unsupported
        |Variable names: myVarName1, myVarName2""".stripMargin
    Try {
      scalaFixture.configureFromFileText(before)
      invokeMoveRefactoring()
    } match {
      case Failure(re: RefactoringErrorHintException) =>
        assertEquals(
          expectedErrorMessage,
          re.getMessage
        )
      case Failure(e) =>
        throw e
      case _ =>
        fail(s"Refactoring succeeded but expected to fail with message: $expectedErrorMessage")
    }
  }

  private def doTest(initialText: String, expectedText: String, className: String): Unit = {
    doRefactoringAction(initialText, className)
    Assert.assertEquals(expectedText, getFile.getText)
  }

  private def doRefactoringAction(
    fileText: String,
    className: String,
  ): Unit = {
    scalaFixture.configureFromFileText(fileText)

    UiInterceptors.register(new UiInterceptors.UiInterceptor[ScalaAnonymousToInnerDialog](classOf[ScalaAnonymousToInnerDialog]) {
      override protected def doIntercept(dialog: ScalaAnonymousToInnerDialog): Unit = {
        Disposer.register(getTestRootDisposable, dialog.getDisposable)
        dialog.setClassName(className)
        dialog.performOKAction()
      }
    })

    invokeMoveRefactoring()
  }

  private def invokeMoveRefactoring(): Unit = {
    new MoveHandler().invoke(getProject, getEditor, getFile, DataContext.EMPTY_CONTEXT)
  }
}
