package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class SubstringZeroTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SubstringZeroInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("substring.with.0.is.redundant")

  def testOnLiteral(): Unit = {
    doTest(
      s"println(\"Hello world!\".${START}substring(0)$END)",
      "println(\"Hello world!\".substring(0))",
      "println(\"Hello world!\")"
    )
  }

  def testOnVal(): Unit = {
    doTest(
      s"val a = \"Hello\"\nprintln(a.${START}substring(0)$END)",
      "val a = \"Hello\"\nprintln(a.substring(0))",
      "val a = \"Hello\"\nprintln(a)"
    )
  }
}
