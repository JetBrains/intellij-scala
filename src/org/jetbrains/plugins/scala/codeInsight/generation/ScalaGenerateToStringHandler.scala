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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}

/**
 * Generate toString method action handler.
 *
 * @author Rado Buransky (buransky.com)
 */
class ScalaGenerateToStringHandler extends LanguageCodeInsightActionHandler {
  /**
   * Main handler method.
   */
  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    if (CodeInsightUtilBase.prepareEditorForWrite(editor) &&
        FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) {
      // Get class at the caret
      GenerationUtil.classAtCaret(editor, psiFile).map { aClass =>
        // Generate toString method signature
        val toStringMethod = createToString(aClass, project)

        // Write it to the source file
        extensions.inWriteAction {
          GenerationUtil.addMembers(aClass, toStringMethod.toList, editor.getDocument)
        }
      }
    }
  }

  /**
   * Determines whether toString can be generated for the class.
   */
  override def isValidFor(editor: Editor, file: PsiFile): Boolean = {
    lazy val isSuitableClass = GenerationUtil.classAtCaret(editor, file) match {
      case Some(c: ScClass) if !c.isCase => true
      case _ => false
    }
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType && isSuitableClass
  }

  override def startInWriteAction(): Boolean = true

  /**
   * Create toString method signature.
   */
  private def createToString(aClass: ScClass, project: Project): Option[ScFunction] = {
    // Get fields from the wizard dialog
    getFields(aClass, project).map { fields =>
      // Fields to string
      val fieldsText = fields.map("$" + _.name).mkString(s"${aClass.getName}(", ", ", ")")

      // Create method
      val methodText = s"""override def toString = s"$fieldsText""""
      ScalaPsiElementFactory.createMethodWithContext(methodText, aClass, aClass.extendsBlock)
    }
  }

  /**
   * Get class fields using wizard dialog.
   */
  private def getFields(aClass: ScClass, project: Project): Option[Seq[ScNamedElement]] = {
    // Not very nice
    if (ApplicationManager.getApplication.isUnitTestMode) {
      Some(GenerationUtil.getAllFields(aClass))
    }
    else {
      // Show wizard
      val wizard = new ScalaGenerateToStringWizard(project, aClass)
      wizard.show()
      if (wizard.isOK)
        // Get fields from the wizard
        Some(wizard.getToStringFields)
      else
        // Nothing to do
        None
    }
  }
}
