package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author t-kameyama
  */
class EmulateMapTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[EmulateMapInspection]

  override protected val hint: String = InspectionBundle.message("replace.with.map")

  def testSeqFoldLeft(): Unit = {
    doTest(
      s"def f(x: Int) = x + 1; Seq(1, 2, 3).${START}foldLeft(Seq.empty[Int])((acc, x) => acc :+ f(x))$END",
      "def f(x: Int) = x + 1; Seq(1, 2, 3).foldLeft(Seq.empty[Int])((acc, x) => acc :+ f(x))",
      "def f(x: Int) = x + 1; Seq(1, 2, 3).map(f)"
    )
  }

  def testSeqFoldLeftWithDifferentTypeStart(): Unit = {
    checkTextHasNoErrors("def f(x: Int) = x + 1; Seq(1, 2, 3).foldLeft(List(1))((acc, x) => acc :+ f(x))")
  }

  def testSeqFoldLeftWithNotEmptyStart(): Unit = {
    checkTextHasNoErrors("def f(x: Int) = x + 1; Seq(1, 2, 3).foldLeft(Seq(1))((acc, x) => acc :+ f(x))")
  }

  def testSeqFoldRight(): Unit = {
    doTest(
      s"def f(x: Int) = x + 1; Seq(1, 2, 3).${START}foldRight(Seq.empty[Int])((x, acc) => f(x) +: acc)$END",
      "def f(x: Int) = x + 1; Seq(1, 2, 3).foldRight(Seq.empty[Int])((x, acc) => f(x) +: acc)",
      "def f(x: Int) = x + 1; Seq(1, 2, 3).map(f)"
    )
  }

}
