package org.jetbrains.plugins.scala.lang.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.Language
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.TestCase.assertNotNull
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors

import java.nio.file.Path
import scala.annotation.unused

abstract class SurroundWithTestBase extends NoSdkFileSetTestBase {

  private def doSurround(project: Project, file: PsiFile, surrounder: Surrounder, startSelection: Int, endSelection: Int): Unit = {
    val fileEditorManager = FileEditorManager.getInstance(project)
    try {
      val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile, 0), false)
      assertNotNull(editor)
      editor.getSelectionModel.setSelection(startSelection, endSelection)
      SurroundWithHandler.invoke(project, editor, file, surrounder)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    } finally fileEditorManager.closeFile(file.getVirtualFile)
  }

  override protected def transform(@unused testName: String, fileText: String): String = {
    val res = SurroundWithTestUtil.prepareFile(fileText)
    val psiFile = createLightFile(res._1)
    val surrounder = ScalaSurroundDescriptors.getSurroundDescriptors.head.getSurrounders
    val runnable: Runnable = () => doSurround(project, psiFile, surrounder(res._4), res._2, res._3)
    WriteCommandAction.runWriteCommandAction(project, runnable)
    psiFile.getText
  }

  override protected def transformExpectedResult(text: String): String = SurroundWithTestUtil.prepareExpectedResult(text)
}

class SurroundWithTest_Scala_2 extends SurroundWithTestBase {
  override protected def relativeTestDataPath: Path = Path.of("surroundWith", "data", "2")
}

class SurroundWithTest_Scala_3 extends SurroundWithTestBase {
  override protected def relativeTestDataPath: Path = Path.of("surroundWith", "data", "3")

  override protected def language: Language = Scala3Language.INSTANCE

  override def setUp(): Unit = {
    super.setUp()
    scalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }
}
