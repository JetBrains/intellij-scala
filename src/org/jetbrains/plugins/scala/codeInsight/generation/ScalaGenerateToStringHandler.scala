package org.jetbrains.plugins.scala.codeInsight.generation

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.generation.ui.{ScalaGenerateToStringWizard, ScalaGenerateEqualsWizard}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, PhysicalSignature}
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}

class ScalaGenerateToStringHandler extends LanguageCodeInsightActionHandler {

  override def isValidFor(editor: Editor, file: PsiFile): Boolean = {
    lazy val isSuitableClass = GenerationUtil.classAtCaret(editor, file) match {
      case Some(c: ScClass) if !c.isCase => true
      case _ => false
    }
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType && isSuitableClass
  }

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return
    if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

    try {
      val aClass = GenerationUtil.classAtCaret(editor, psiFile).getOrElse(return)
      val toStringMethod = Some(createToString(aClass, project))

      extensions.inWriteAction {
        GenerationUtil.addMembers(aClass, toStringMethod.toList, editor.getDocument)
      }
    }
    finally {
    }

  }

  override def startInWriteAction(): Boolean = true

  private def createToString(aClass: ScClass, project: Project): ScFunction = {
    val declText = "def toString: String"

    val fields = getFields(aClass, project).map("$" + _.name)
    val fieldsText = fields.mkString(s"${aClass.getName}(", ", ", ")")

    val methodText = s"""override def toString = s"$fieldsText""""
    ScalaPsiElementFactory.createMethodWithContext(methodText, aClass, aClass.extendsBlock)
  }

  private def getFields(aClass: ScClass, project: Project): Seq[ScNamedElement] = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      GenerationUtil.getAllFields(aClass)
    }
    else {
      val wizard = new ScalaGenerateToStringWizard(project, aClass)
      wizard.show()
      if (wizard.isOK)
        wizard.getToStringFields
      else
        Nil
    }
  }
}
