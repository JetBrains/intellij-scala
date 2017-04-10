package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil

/**
  * @author mattfowler
  */
class IfElseToFilteredOptionInspectionTest extends OperationsOnCollectionInspectionTest {
  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[IfElseToFilteredOptionInspection]

  override protected val hint: String = "Replace if with filtered option"

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  private val evenFunction = "def isEven(x:Int) = x % 2 == 0;"

  def testShouldReplaceWhenReturningSome() = {
    doTest(
      s"$evenFunction ${START}if (isEven(2)) Some(2) else None$END",
      s"$evenFunction if (isEven(2)) Some(2) else None",
      s"$evenFunction Option(2).filter(isEven)"
    )
  }

  def foo(a:Int)(b:Int) = a + b

  def testShouldReplaceWhenReturningOption() = {
    doTest(
      s"$evenFunction ${START}if (isEven(2)) Option(2) else None$END",
      s"$evenFunction if (isEven(2)) Option(2) else None",
      s"$evenFunction Option(2).filter(isEven)"
    )
  }

  def testShouldReplaceWithMethodCallAsParam() = {
    val getInt = "def getInt() = 2;"
    doTest(
      s"$evenFunction $getInt ${START}if (isEven(getInt())) Option(getInt()) else None$END",
      s"$evenFunction $getInt if (isEven(getInt())) Option(getInt()) else None",
      s"$evenFunction $getInt Option(getInt()).filter(isEven)"
    )
  }

  def testShouldNotShowIfMethodParametersAreNotEqual() =
    checkTextHasNoErrors(s"$evenFunction if (isEven(4)) Option(2) else None")

}