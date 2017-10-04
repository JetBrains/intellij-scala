package org.jetbrains.plugins.scala.codeInsight.generation

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaCodeInsightActionHandler
import org.jetbrains.plugins.scala.codeInsight.generation.GenerationUtil._
import org.jetbrains.plugins.scala.codeInsight.generation.ui.ScalaGenerateToStringWizard
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * Generate toString metod action.
  *
  * @author Rado Buransky (buransky.com)
  */
class ScalaGenerateToStringAction extends ScalaBaseGenerateAction(new ScalaGenerateToStringHandler)

class ScalaGenerateToStringHandler extends ScalaCodeInsightActionHandler {

  import ScalaGenerateToStringHandler._

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return
    if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

    findTypeDefinition(editor, file).foreach { definition =>
      val toStringMethod = createToString(definition, project)

      inWriteAction {
        addMembers(definition, toStringMethod.toList, editor.getDocument)
      }
    }
  }

  /**
    * Determines whether toString can be generated for the class.
    */
  override def isValidFor(editor: Editor, file: PsiFile): Boolean =
    super.isValidFor(editor, file) && findTypeDefinition(editor, file).exists(isTypeDefinitionValid)
}

object ScalaGenerateToStringHandler {
  private def findTypeDefinition(editor: Editor, file: PsiFile): Option[ScTypeDefinition] =
    elementOfTypeAtCaret(editor, file, classOf[ScClass], classOf[ScObject], classOf[ScTrait])

  private def isTypeDefinitionValid(definition: ScTypeDefinition): Boolean = definition match {
    case _: ScTrait => true
    case _: ScClass | _: ScObject => !definition.isCase
    case _ => false
  }

  /**
    * Create toString method signature.
    */
  private def createToString(definition: ScTypeDefinition, project: Project): Option[ScFunction] = {
    val typeName = definition match {
      case _: ScObject if definition.name.last == '$' => definition.name.dropRight(1)
      case _ => definition.name
    }

    showWizard(definition, project).map { result =>
      val (fields, withFieldNames) = result

      def getDollarName(elem: ScNamedElement) = "$" + elem.name

      val fieldsWithNames = if (withFieldNames)
        fields.map(field => field.name + "=" + getDollarName(field))
      else
        fields.map(getDollarName)

      val fieldsText = fieldsWithNames.mkString(s"$typeName(", ", ", ")")
      val methodText = s"""override def toString = s"$fieldsText""""
      ScalaPsiElementFactory.createMethodWithContext(methodText, definition, definition.extendsBlock)
    }
  }

  /**
    * Get class fields using wizard dialog.
    */
  private def showWizard(definition: ScTypeDefinition, project: Project): Option[(Seq[ScNamedElement], Boolean)] = {
    val allSuitableMembers = getAllSuitableMembers(definition)
    if (ApplicationManager.getApplication.isUnitTestMode) {
      Some(allSuitableMembers, true)
    }
    else {
      Some(new ScalaGenerateToStringWizard(project, allSuitableMembers))
        .filter(_.showAndGet())
        .map { wizard =>
          (wizard.getToStringFields, wizard.withFieldNames)
        }
    }
  }

  private def getAllSuitableMembers(definition: ScTypeDefinition): Seq[ScNamedElement] =
    getAllFields(definition) ++ getAllParameterlessMethods(definition)
}
