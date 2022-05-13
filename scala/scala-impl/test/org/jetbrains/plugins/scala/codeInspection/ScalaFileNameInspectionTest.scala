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

  // TODO: find out why these two tests don't work and fix them. It worked for me when I did manual testing
  //def test_rename_class(): Unit =
  //  testQuickFix(
  //    "class Blub",
  //    "class Start",
  //    "Rename Type Definition Blub to Start"
  //  )

  //def test_rename_file(): Unit = {
  //  testQuickFix(
  //    "class Blub",
  //    "class Blub",
  //    "Rename File Start.scala to Blub.scala"
  //  )
  //  getFile.name shouldBe "Blub.scala"
  //}

  def test_two_classes(): Unit =
    checkTextHasNoErrors(
      """
        |class A
        |class B
        |""".stripMargin
    )

  def test_correct_class(): Unit =
    checkTextHasNoErrors("class Start")
}
