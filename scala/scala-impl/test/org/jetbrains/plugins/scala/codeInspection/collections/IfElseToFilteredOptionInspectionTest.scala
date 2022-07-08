package org.jetbrains.plugins.scala.codeInspection.collections

class IfElseToFilteredOptionInspectionTest extends OperationsOnCollectionInspectionTest {
  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[IfElseToFilteredOptionInspection]

  override protected val hint: String = "Replace if with filtered option"

  private val evenFunction = "def isEven(x:Int) = x % 2 == 0;"

  def testShouldReplaceWhenReturningSome(): Unit = {
    doTest(
      s"$evenFunction ${START}if (isEven(2)) Some(2) else None$END",
      s"$evenFunction if (isEven(2)) Some(2) else None",
      s"$evenFunction Some(2).filter(isEven)"
    )
  }

  def testShouldReplaceWhenReturningOption(): Unit = {
    doTest(
      s"$evenFunction ${START}if (isEven(2)) Option(2) else None$END",
      s"$evenFunction if (isEven(2)) Option(2) else None",
      s"$evenFunction Option(2).filter(isEven)"
    )
  }

  def testShouldWorkEvenIfWhitespacePresent(): Unit = {
    doTest(
      s"$evenFunction ${START}if (isEven(2   )) Option( 2) else None$END",
      s"$evenFunction if (isEven(2   )) Option( 2) else None",
      s"$evenFunction Option(2).filter(isEven)"
    )
  }

  def testShouldNotReplaceWithMethodCallAsParam(): Unit = {
    val getInt = "def getInt() = 2;"
    checkTextHasNoErrors(s"$evenFunction $getInt ${START}if (isEven(getInt())) Option(getInt()) else None$END")
  }

  def testShouldNotShowIfMethodParametersAreNotEqual(): Unit =
    checkTextHasNoErrors(s"$evenFunction if (isEven(4)) Option(2) else None")

}