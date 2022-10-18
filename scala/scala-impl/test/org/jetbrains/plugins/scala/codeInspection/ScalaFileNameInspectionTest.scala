package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

class ScalaFileNameInspectionTest extends ScalaInspectionTestBase with AssertionMatchers {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaFileNameInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("fileName.does.not.match")

  override protected def onFileCreated(file: PsiFile): Unit =
    new RenameProcessor(getProject, file, "Start.scala", false, false).run()

  def test_presentation(): Unit =
    checkTextHasError(s"class ${START}Blub$END")

  def test_two_classes(): Unit =
    checkTextHasNoErrors(
      """
        |class A
        |class B
        |""".stripMargin
    )

  def test_class_and_object(): Unit =
    checkTextHasError(
      s"""
        |class ${START}A${END}
        |object ${START}A${END}
        |""".stripMargin
    )

  def test_correct_class(): Unit =
    checkTextHasNoErrors("class Start")
}
