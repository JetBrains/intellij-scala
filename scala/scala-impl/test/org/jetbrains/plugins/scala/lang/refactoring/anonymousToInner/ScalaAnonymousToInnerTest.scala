package org.jetbrains.plugins.scala.lang.refactoring.anonymousToInner

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaVariableData
import org.jetbrains.plugins.scala.lang.refactoring.move.anonymousToInner.ScalaAnonymousToInnerHandler
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import java.util.concurrent.TimeUnit

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
        |  class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
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
        |  class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
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
        |  class IntIterator(input: Iterator[Int], num: Int) extends Iterator[Int] {
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
        |  class IntIterator(input: Iterator[Int], num: Int) extends Iterator[Int] {
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
        |class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
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
        |class IntIterator(sliceSize: Int) extends Iterator[Int] {
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
        |  class IntIterator(input: Iterator[Byte], someNum: Int) extends Iterator[Int] {
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

  def testFailOnVarOutsideAnonClass(): Unit = {
    val before =
      s"""object MyClass {
         |  var x = 0
         |  def parse(input: Iterator[Byte], someNum: Int): Option[Iterator[Int]] =
         |    if (input.hasNext) {
         |      Some(new Ite${Caret}rator[Int] {
         |        def hasNext = input.hasNext
         |
         |        def next = someNum + x
         |      })
         |    } else
         |      None
         |}
         |""".stripMargin

    val className = "IntIterator"
    val RefactoringActionResult(_, extendsBlock, variables, _) = doRefactoringAction(before, className)

    Assert.assertTrue(ScalaAnonymousToInnerHandler.containsVarsOutOfScope(extendsBlock, variables))
  }


  private def doTest(initialText: String, expectedText: String, className: String): Unit = {
    val result = doRefactoringAction(initialText, className)
    Assert.assertEquals(expectedText, result.scalaFile.getText)
  }

  private case class RefactoringActionResult(scalaFile: ScalaFile, extendsBlock: ScExtendsBlock, variables: Array[ScalaVariableData], targetContainer: Either[ScFile, ScTemplateDefinition])

  private def doRefactoringAction(initialText: String, className: String): RefactoringActionResult = {
    scalaFixture.configureFromFileText(initialText)

    val editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContextFromFocusAsync.blockingGet(5, TimeUnit.SECONDS))
    val caretModel = editor.getCaretModel
    assert(caretModel.getCaretCount == 1, "Expected exactly one caret.")
    assert(caretModel.getOffset > 0, s"Not specified caret marker in test case. Use <caret> in scala file for this.")

    val scalaFile = getFile.asInstanceOf[ScalaFile]

    val element = scalaFile.findElementAt(caretModel.getOffset)
    val newTemplateDefinition = Option(PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])).getOrElse(
      throw new Exception("expected to find the `new` keyword before the anonymous type")
    )

    val extendsBlock = newTemplateDefinition.extendsBlock
    val (variables, targetContainer) = ScalaAnonymousToInnerHandler.parseInitialExtendsBlock(extendsBlock)
    ScalaAnonymousToInnerHandler.performRefactoring(getProject, className, variables, extendsBlock, newTemplateDefinition, targetContainer)

    RefactoringActionResult(scalaFile, extendsBlock, variables, targetContainer)
  }
}
