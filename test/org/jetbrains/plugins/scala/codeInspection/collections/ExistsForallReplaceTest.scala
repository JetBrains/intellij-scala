package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
  * Ignat Loskutov
  */
abstract class ExistsForallReplaceTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ExistsForallReplaceInspection]
}

class ReplaceForallWithExistsTest extends ExistsForallReplaceTest {

  override protected val hint: String =
    InspectionBundle.message("replace.with.exists")

  def test_1() {
    val selected = s"""$START!Seq("").forall(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").forall(!_.isEmpty)"""
    val result = """Seq("").exists(_.isEmpty)"""
    testQuickFix(text, result, hint)
  }
}

class ReplaceSmthWithNotContainsTest extends ExistsForallReplaceTest {

  override protected val hint: String =
    InspectionBundle.message("replace.with.forall")

  def test_1() {
    val selected = s"""$START!Seq("").exists(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").exists(!_.isEmpty)"""
    val result = """Seq("").forall(_.isEmpty)"""
    testQuickFix(text, result, hint)
  }
}
