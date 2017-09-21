package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil.{findCommonParent, getParentOfType}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.{getExpression, getTypeElement}
import org.jetbrains.plugins.scala.lang.refactoring.util._
import org.jetbrains.plugins.scala.util.TestUtils._

abstract class AbstractIntroduceVariableValidatorTestBase(kind: String) extends ActionTestBase(
  Option(System.getProperty("path")).getOrElse(s"""$getTestDataPath/introduceVariable/validator/$kind""")
) {
  protected var myEditor: Editor = _
  protected var fileEditorManager: FileEditorManager = _
  protected var myFile: PsiFile = _

  import AbstractIntroduceVariableValidatorTestBase._

  override def transform(testName: String, data: Array[String]): String = {
    setSettings()
    val fileText = data(0)
    val psiFile = createPseudoPhysicalScalaFile(getProject, fileText)
    processFile(psiFile)
  }

  protected def removeAllMarker(text: String): String = {
    val index = text.indexOf(ALL_MARKER)
    myOffset = index - 1
    text.substring(0, index) + text.substring(index + ALL_MARKER.length)
  }

  private def processFile(file: PsiFile): String = {
    var replaceAllOccurrences = false
    var fileText = file.getText
    var startOffset = fileText.indexOf(BEGIN_MARKER)
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER)
      replaceAllOccurrences = true
      fileText = removeAllMarker(fileText)
    }
    else {
      replaceAllOccurrences = false
      fileText = removeBeginMarker(fileText)
    }
    val endOffset = fileText.indexOf(END_MARKER)
    fileText = removeEndMarker(fileText)

    myFile = createPseudoPhysicalScalaFile(getProject, fileText)
    fileEditorManager = FileEditorManager.getInstance(getProject)
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject, myFile.getVirtualFile, 0), false)
    myEditor.getSelectionModel.setSelection(startOffset, endOffset)

    try {
      val maybeValidator = getValidator(myFile)(getProject, myEditor)
      maybeValidator.toSeq
        .flatMap(_.findConflicts(getName(fileText), replaceAllOccurrences))
        .map(_._2)
        .toSet[String]
        .mkString("\n")
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile)
      myEditor = null
    }
  }

  protected def getName(fileText: String): String
}

object AbstractIntroduceVariableValidatorTestBase {
  private val ALL_MARKER = "<all>"

  def getValidator(file: PsiFile)
                  (implicit project: Project, editor: Editor): Option[ScalaValidator] = {
    implicit val selectionModel: SelectionModel = editor.getSelectionModel

    getParentOfType(file.findElementAt(selectionModel.getSelectionStart), classOf[ScExpression], classOf[ScTypeElement]) match {
      case _: ScExpression => getExpression(file).map(getVariableValidator(_, file))
      case _: ScTypeElement => getTypeElement(file).map(getTypeValidator(_, file))
      case _ => None
    }
  }

  import ScalaRefactoringUtil._

  private[this] def getContainerOne(file: PsiFile, length: Int)
                                   (implicit selectionModel: SelectionModel): PsiElement = {
    val origin = file.findElementAt(selectionModel.getSelectionStart)
    val bound = file.findElementAt(selectionModel.getSelectionEnd - 1)

    val commonParentOne = findCommonParent(origin, bound)
    ScalaPsiUtil.getParentOfType(commonParentOne, length == 1, classOf[ScalaFile], classOf[ScBlock], classOf[ScTemplateBody])
  }

  private[this] def getVariableValidator(expression: ScExpression, file: PsiFile)
                                        (implicit selectionModel: SelectionModel): ScalaVariableValidator = {
    val occurrences = getOccurrenceRanges(unparExpr(expression), fileEncloser(file).orNull)
    val containerOne = getContainerOne(file, occurrences.length)

    val parent = commonParent(file, occurrences: _*)
    new ScalaVariableValidator(expression, occurrences.isEmpty, enclosingContainer(parent), containerOne)
  }

  private[this] def getTypeValidator(typeElement: ScTypeElement, file: PsiFile)
                                    (implicit selectionModel: SelectionModel): ScalaTypeValidator = {
    val occurrences = getTypeElementOccurrences(typeElement, fileEncloser(file).orNull)
    val containerOne = getContainerOne(file, occurrences.length)

    val parent = findCommonParent(occurrences: _*)
    new ScalaTypeValidator(typeElement, occurrences.isEmpty, enclosingContainer(parent), containerOne)
  }

}
