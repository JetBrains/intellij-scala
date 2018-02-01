package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection
import org.junit.experimental.categories.Category

/**
  * @author adkozlov
  */
@Category(Array(classOf[PerfCycleTests]))
class OverridingAnnotatorTest3 extends ScalaInspectionTestBase {

  override protected def shouldPass: Boolean = false

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]

  override protected val description: String =
    "overriding variable name in class Abs of type String"

  def testScl7536() {
    checkTextHasError(
      s"""
         |class Abs(var name: String){ }         |
         |class AbsImpl(${START}override$END var name: String) extends Abs(name){ }
      """.stripMargin)
  }
}
