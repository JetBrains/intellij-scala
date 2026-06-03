package org.jetbrains.plugins.scala.lang.refactoring.move.anonymousToInner

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.psi.{PsiManager, PsiNameHelper}
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.util.{CommonRefactoringUtil, ParameterTablePanel, RefactoringMessageUtil, VariableData}
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaVariableData

import java.awt.BorderLayout
import javax.swing.{JComponent, JPanel}
import scala.jdk.CollectionConverters._

/**
 * @note original implementation was inspired by [[com.intellij.refactoring.anonymousToInner.AnonymousToInnerDialog]]
 */
class ScalaAnonymousToInnerDialog(
  project: Project,
  extendsBlock: ScExtendsBlock,
  _variables: Seq[ScalaVariableData],
  target: Either[ScFile, ScTemplateDefinition]
) extends DialogWrapper(project) {

  //This modifiable array is modified directly by `ParameterTablePanel`
  //NOTE: it's not covered by tests, because it implies some UI interaction
  private val variablesModifiableModel: Array[ScalaVariableData] = _variables.toArray

  setTitle(ScalaBundle.message("move.anonymousToInner.dialog.title"))

  private val classNameField: NameSuggestionsField = new NameSuggestionsField(project)
  classNameField.setSuggestions(suggestNewClassNames())
  classNameField.selectNameWithoutExtension()

  init()

  private def suggestNewClassNames(): Array[String] =
    extendsBlock.superTypes.head match {
      case pType: ScParameterizedType =>
        val name = pType.designator.toString
        val typeParameters = pType.typeArguments.map(_.toString).mkString + name
        Array(typeParameters, "My" + name)
      case designator: ScDesignatorType =>
        val name = designator.toString
        Array("My" + name)
    }

  override protected def doOKAction(): Unit = {
    @DialogMessage var errorString: String = null
    val innerClassName = getClassName
    val manager = PsiManager.getInstance(project)
    if (innerClassName.isEmpty) errorString = JavaRefactoringBundle.message("anonymousToInner.no.inner.class.name")
    else {
      if (!PsiNameHelper.getInstance(manager.getProject).isIdentifier(innerClassName)) {
        errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(innerClassName)
      }
      else {
        val existingTemplateDefinitionNames: Set[String] = target.fold(
          file =>
            file.getClassNames.asScala.toSet,
          template =>
            template.extendsBlock
              .getOrCreateTemplateBody
              .getChildren
              .collect { case c: ScTemplateDefinition => c.name }
              .toSet
        )

        if (existingTemplateDefinitionNames.contains(innerClassName)) {
          errorString = JavaRefactoringBundle.message("inner.class.exists", innerClassName, target.map(_.name).getOrElse("TOP LEVEL DEFINTION"))
        }
      }
    }

    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(ScalaAnonymousToInnerHandler.RefactoringTitle, errorString, HelpID.ANONYMOUS_TO_INNER, project)
    } else {
      super.doOKAction()
    }
    classNameField.requestFocusInWindow
  }

  override protected def createNorthPanel: JComponent =
    FormBuilder
      .createFormBuilder
      .addLabeledComponent(JavaRefactoringBundle.message("anonymousToInner.class.name.label.text"), classNameField)
      .getPanel

  override def getPreferredFocusedComponent: JComponent = classNameField.getFocusableComponent

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout)
    panel.add(createParametersPanel, BorderLayout.CENTER)
    panel
  }

  def getClassName: String = classNameField.getEnteredName
  @TestOnly
  def setClassName(name: String): Unit = classNameField.setName(name)

  def getVariables: Seq[ScalaVariableData] =
    variablesModifiableModel.filter(_.passAsParameter).toSeq

  private def createParametersPanel: ParameterTablePanel = {
    val panel = new ParameterTablePanel(project, variablesModifiableModel.asInstanceOf[Array[VariableData]], extendsBlock) {
      override protected def updateSignature(): Unit = {
      }

      override protected def doEnterAction(): Unit = {
        clickDefaultButton()
      }

      override protected def doCancelAction(): Unit = {
        this.asInstanceOf[DialogWrapper].doCancelAction()
      }
    }
    panel.setBorder(IdeBorderFactory.createTitledBorder(JavaRefactoringBundle.message("anonymousToInner.parameters.panel.border.title"), false))
    panel
  }

  override protected def getHelpId: String = HelpID.ANONYMOUS_TO_INNER
}
