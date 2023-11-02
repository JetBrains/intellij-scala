package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.lang.Language
import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil.{findCommonParent, getParentOfType}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.OccurrenceData.ReplaceOptions
import org.jetbrains.plugins.scala.lang.refactoring.util._

abstract class AbstractIntroduceVariableValidatorTestBase(kind: String)
  extends ActionTestBase("/refactoring/introduceVariable/validator/" + kind) {

  import AbstractIntroduceVariableValidatorTestBase._

  protected def language: Language = ScalaLanguage.INSTANCE

  protected var myFixture: ScalaIntroduceVariableTestFixture = _

  override protected def setUp(project: Project): Unit = {
    super.setUp(project)
    myFixture = new ScalaIntroduceVariableTestFixture(project, None, language = language)
    myFixture.setUp()
  }

  override protected def tearDown(project: Project): Unit = {
    myFixture.tearDown()
    super.tearDown(project)
  }

  protected override def transform(
    testName: String,
    testFileText: String,
    project: Project
  ): String = {
    val (fileTextNew, options) = IntroduceVariableUtils.extractNameFromLeadingComment(testFileText)
    myFixture.configureFromText(fileTextNew)

    val replaceAllOccurrences = options.replaceAllOccurrences.getOrElse(ReplaceOptions.DefaultInTests.replaceAllOccurrences)

    doTest(replaceAllOccurrences, fileTextNew, project)
  }

  protected def doTest(
    replaceAllOccurrences: Boolean,
    fileText: String,
    project: Project
  ): String = {
    val maybeValidator = getValidator(myFixture.psiFile)(project, myFixture.editor)
    val conflicts = maybeValidator.toSeq.flatMap(_.findConflicts(getName(fileText), replaceAllOccurrences))
    conflicts.map(_._2).toSet.mkString("\n")
  }

  protected def getName(fileText: String): String
}

object AbstractIntroduceVariableValidatorTestBase {

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