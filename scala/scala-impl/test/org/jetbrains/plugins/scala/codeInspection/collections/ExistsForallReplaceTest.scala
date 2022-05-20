package org.jetbrains.plugins.scala
package codeInspection
package collections

/**
  * Ignat Loskutov
  */
abstract class ExistsForallReplaceTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ExistsForallReplaceInspection]
}

class ReplaceForallWithExistsTest extends ExistsForallReplaceTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.exists")

  def test_1(): Unit = {
    val selected = s"""$START!Seq("").forall(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").forall(!_.isEmpty)"""
    val result = """Seq("").exists(_.isEmpty)"""
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"""$START!Seq("").forall(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").forall(s => !s.isEmpty)"""
    val result = """Seq("").exists(s => s.isEmpty)"""
    testQuickFix(text, result, hint)
  }
}

class ReplaceSmthWithNotContainsTest extends ExistsForallReplaceTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.forall")

  def test_1(): Unit = {
    val selected = s"""$START!Seq("").exists(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").exists(!_.isEmpty)"""
    val result = """Seq("").forall(_.isEmpty)"""
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"""$START!Seq("").exists(!_.isEmpty)$END"""
    checkTextHasError(selected)
    val text = """!Seq("").exists((s: String) => !s.isEmpty)"""
    val result = """Seq("").forall((s: String) => s.isEmpty)"""
    testQuickFix(text, result, hint)
  }
}
