package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil.{findCommonParent, getParentOfType}
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.refactoring.util._
import org.jetbrains.plugins.scala.util.TestUtils._

import scala.annotation.nowarn

abstract class AbstractIntroduceVariableValidatorTestBase(kind: String)
  extends ActionTestBase("/refactoring/introduceVariable/validator/" + kind) {

  protected var myEditor: Editor = _
  protected var fileEditorManager: FileEditorManager = _
  protected var myFile: PsiFile = _

  import AbstractIntroduceVariableValidatorTestBase._

  protected def removeAllMarker(text: String): String = {
    val index = text.indexOf(ALL_MARKER)
    myOffset = index - 1
    text.substring(0, index) + text.substring(index + ALL_MARKER.length)
  }

  protected def fileExtension: String = "scala"

  protected override def transform(testName: String,
                                   fileText: String,
                                   project: Project): String = {
    var replaceAllOccurrences = false
    var testFileText = fileText
    var startOffset = testFileText.indexOf(BEGIN_MARKER)
    if (startOffset < 0) {
      startOffset = testFileText.indexOf(ALL_MARKER)
      replaceAllOccurrences = true
      testFileText = removeAllMarker(testFileText)
    }
    else {
      replaceAllOccurrences = false
      testFileText = removeBeginMarker(testFileText)
    }
    val endOffset = testFileText.indexOf(END_MARKER)
    testFileText = removeEndMarker(testFileText)

    val fileName = s"dummy.$fileExtension"

    //noinspection ScalaDeprecation (It's ok to rely on auto-file type detection from file name in tests)
    myFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, testFileText): @nowarn("cat=deprecation")
    val virtualFile = myFile.getViewProvider.getVirtualFile

    fileEditorManager = FileEditorManager.getInstance(project)
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, virtualFile, 0), false)
    myEditor.getSelectionModel.setSelection(startOffset, endOffset)

    try {
      doTest(replaceAllOccurrences, testFileText, project)
    } finally {
      fileEditorManager.closeFile(virtualFile)
      myEditor = null
    }
  }

  protected def doTest(replaceAllOccurrences: Boolean, fileText: String,
                       project: Project): String = {
    val maybeValidator = getValidator(myFile)(project, myEditor)
    maybeValidator.toSeq
      .flatMap(_.findConflicts(getName(fileText), replaceAllOccurrences))
      .map(_._2)
      .toSet[String]
      .mkString("\n")
  }

  protected def getName(fileText: String): String
}

object AbstractIntroduceVariableValidatorTestBase {
  private val ALL_MARKER = "<all>"

  def getValidator(file: PsiFile)
                  (implicit project: Project, editor: Editor): Option[ScalaValidator] = {
    implicit val selectionModel: SelectionModel = editor.getSelectionModel

    getParentOfType(file.findElementAt(selectionModel.getSelectionStart), classOf[ScExpression], classOf[ScTypeElement]) match {
      case _: ScExpression => ScalaRefactoringUtil.getSelectedExpression(file).map(getVariableValidator(_, file))
      case _: ScTypeElement => ScalaRefactoringUtil.getSelectedTypeElement(file).map(getTypeValidator(_, file))
      case _ => None
    }
  }

  private[this] def getContainerOne(file: PsiFile, length: Int)
                                   (implicit selectionModel: SelectionModel): PsiElement = {
    val origin = file.findElementAt(selectionModel.getSelectionStart)
    val bound = file.findElementAt(selectionModel.getSelectionEnd - 1)

    val commonParentOne = findCommonParent(origin, bound)

    val classes = Seq(classOf[ScalaFile], classOf[ScBlock], classOf[ScTemplateBody])
    (length match {
      case 1 => commonParentOne.parentOfType(classes)
      case _ => commonParentOne.nonStrictParentOfType(classes)
    }).orNull
  }

  private[this] def getVariableValidator(expression: ScExpression, file: PsiFile)
                                        (implicit selectionModel: SelectionModel): ScalaVariableValidator = {
    val occurrences = ScalaRefactoringUtil.getOccurrenceRanges(expression, ScalaRefactoringUtil.fileEncloser(file, selectionModel.getSelectionStart).orNull)
    ScalaVariableValidator(file, expression, occurrences)
  }

  private[this] def getTypeValidator(typeElement: ScTypeElement, file: PsiFile)
                                    (implicit selectionModel: SelectionModel): ScalaTypeValidator = {
    val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, ScalaRefactoringUtil.fileEncloser(file, selectionModel.getSelectionStart).orNull)
    val containerOne = getContainerOne(file, occurrences.length)

    val parent = findCommonParent(occurrences: _*)
    new ScalaTypeValidator(typeElement, occurrences.isEmpty, ScalaRefactoringUtil.enclosingContainer(parent), containerOne)
  }

}