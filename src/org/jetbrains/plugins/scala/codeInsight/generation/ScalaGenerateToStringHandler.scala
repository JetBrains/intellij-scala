package org.jetbrains.plugins.scala.codeInsight.generation

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.generation.ui.ScalaGenerateToStringWizard
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScObject, ScClass}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}

/**
 * Generate toString method action handler.
 *
 * @author Rado Buransky (buransky.com)
 */
class ScalaGenerateToStringHandler extends LanguageCodeInsightActionHandler {
  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    if (CodeInsightUtilBase.prepareEditorForWrite(editor) &&
        FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) {
      GenerationUtil.elementOfTypeAtCaret(editor, psiFile, classOf[ScClass],
        classOf[ScObject], classOf[ScTrait]).map { aType =>
        val toStringMethod = createToString(aType, project)

        extensions.inWriteAction {
          GenerationUtil.addMembers(aType, toStringMethod.toList, editor.getDocument)
        }
      }
    }
  }

  /**
   * Determines whether toString can be generated for the class.
   */
  override def isValidFor(editor: Editor, file: PsiFile): Boolean = {
    lazy val isSuitableClass = GenerationUtil.elementOfTypeAtCaret(editor, file, classOf[ScClass], classOf[ScObject],
      classOf[ScTrait]) match {
      case Some(c: ScClass) if !c.isCase => true
      case Some(c: ScObject) if !c.isCase => true
      case Some(c: ScTrait) => true
      case _ => false
    }
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType && isSuitableClass
  }

  override def startInWriteAction(): Boolean = true

  /**
   * Create toString method signature.
   */
  private def createToString(aType: ScTypeDefinition, project: Project): Option[ScFunction] = {
    val typeName = aType match {
      case _: ScObject if aType.name.last == '$' => aType.name.dropRight(1)
      case _ => aType.name
    }

    showWizard(aType, project).map { result =>
      val (fields, withFieldNames) = result

      def getDollarName(elem: ScNamedElement) = "$" + elem.name
      val fieldsWtihNames = if (withFieldNames)
        fields.map(field => field.name + "=" + getDollarName(field))
      else
        fields.map(getDollarName)

      val fieldsText = fieldsWtihNames.mkString(s"$typeName(", ", ", ")")
      val methodText = s"""override def toString = s"$fieldsText""""
      ScalaPsiElementFactory.createMethodWithContext(methodText, aType, aType.extendsBlock)
    }
  }

  /**
   * Get class fields using wizard dialog.
   */
  private def showWizard(aType: ScTypeDefinition, project: Project): Option[(Seq[ScNamedElement], Boolean)] = {
    val allSuitableMembers = getAllSuitableMembers(aType)
    if (ApplicationManager.getApplication.isUnitTestMode) {
      Some(allSuitableMembers, true)
    }
    else {
      val wizard = new ScalaGenerateToStringWizard(project, allSuitableMembers)
      if (wizard.showAndGet())
        Some(wizard.getToStringFields, wizard.withFieldNames)
      else
        None
    }
  }

  private def getAllSuitableMembers(aType: ScTypeDefinition): Seq[ScNamedElement] =
    GenerationUtil.getAllFields(aType) ++ GenerationUtil.getAllParameterlessMethods(aType)
}
