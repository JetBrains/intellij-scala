package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author t-kameyama
  */
class IndexBoundsCheckTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[IndexBoundsCheckInspection]

  override protected val hint: String =
    InspectionBundle.message("ifstmt.to.lift")

  def test(): Unit = {
    doTest(
      s"val seq = Seq(1, 2, 3); val i = 1; val s = ${START}if (i < seq.length) Some(seq(i)) else None$END",
      "val seq = Seq(1, 2, 3); val i = 1; val s = if (i < seq.length) Some(seq(i)) else None",
      "val seq = Seq(1, 2, 3); val i = 1; val s = seq.lift(i)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"val seq = Seq(1, 2, 3); val i = 1; val s = ${START}if (seq.length > i) Some(seq(i)) else None$END",
      "val seq = Seq(1, 2, 3); val i = 1; val s = if (seq.length > i) Some(seq(i)) else None",
      "val seq = Seq(1, 2, 3); val i = 1; val s = seq.lift(i)"
    )
  }

}
