package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
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

    try {
      val validator = getValidator(getProject, myEditor)(myFile, (startOffset, endOffset))
      val typeName = getName(fileText)

      validator.findConflicts(typeName, replaceAllOccurrences)
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

  def getValidator(project: Project, editor: Editor)
                  (implicit file: PsiFile, offsets: (Int, Int)): ScalaValidator = {
    val (startOffset, endOffset) = offsets

    PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), classOf[ScExpression], classOf[ScTypeElement]) match {
      case _: ScExpression =>
        val (expression: ScExpression, _) = getExpression(project, editor, file, startOffset, endOffset).get
        getVariableValidator(expression)
      case _: ScTypeElement =>
        val typeElement = getTypeElement(project, editor, file, startOffset, endOffset).get
        getTypeValidator(typeElement)
      case _ => null
    }
  }

  import ScalaRefactoringUtil._

  private[this] def getContainerOne(length: Int)
                                   (implicit file: PsiFile, offsets: (Int, Int)): PsiElement = {
    val (startOffset, endOffset) = offsets

    val commonParentOne = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1))
    ScalaPsiUtil.getParentOfType(commonParentOne, length == 1, classOf[ScalaFile], classOf[ScBlock], classOf[ScTemplateBody])
  }

  private[this] def getVariableValidator(expression: ScExpression)
                                        (implicit file: PsiFile, offsets: (Int, Int)): ScalaVariableValidator = {
    val encloser = fileEncloser(offsets._1, file)
    val occurrences = getOccurrenceRanges(unparExpr(expression), encloser)
    val containerOne = getContainerOne(occurrences.length)

    val parent = commonParent(file, occurrences: _*)
    new ScalaVariableValidator(expression, occurrences.isEmpty, enclosingContainer(parent), containerOne)
  }

  private[this] def getTypeValidator(typeElement: ScTypeElement)
                                    (implicit file: PsiFile, offsets: (Int, Int)): ScalaTypeValidator = {
    val encloser = fileEncloser(offsets._1, file)
    val occurrences = getTypeElementOccurrences(typeElement, encloser)
    val containerOne = getContainerOne(occurrences.length)

    val parent = PsiTreeUtil.findCommonParent(occurrences: _*)
    new ScalaTypeValidator(typeElement, occurrences.isEmpty, enclosingContainer(parent), containerOne)
  }

}
