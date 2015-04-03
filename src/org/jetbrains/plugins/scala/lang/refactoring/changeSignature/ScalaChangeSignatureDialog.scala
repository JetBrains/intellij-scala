package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.util
import javax.swing.JComponent

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightLevelUtil}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.refactoring.changeSignature.{CallerChooserBase, ChangeSignatureDialogBase}
import com.intellij.refactoring.ui.VisibilityPanelBase
import com.intellij.refactoring.{BaseRefactoringProcessor, RefactoringBundle}
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Any, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaComboBoxVisibilityPanel
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
* Nikolay.Tropin
* 2014-08-29
*/
class ScalaChangeSignatureDialog(val project: Project, method: ScalaMethodDescriptor)
        extends {
          private var defaultValuesUsagePanel: DefaultValuesUsagePanel = null
        }
        with ChangeSignatureDialogBase[ScalaParameterInfo,
                                          ScFunction,
                                          String,
                                          ScalaMethodDescriptor,
                                          ScalaParameterTableModelItem,
                                          ScalaParameterTableModel](project, method, false, method.fun)
        with ScalaParameterTableListViewProvider {

  override def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE

  override def createCallerChooser(title: String, treeToReuse: Tree, callback: Consumer[util.Set[ScFunction]]): CallerChooserBase[ScFunction] = null

  override def createRefactoringProcessor(): BaseRefactoringProcessor = {
    val changeInfo =
      new ScalaChangeInfo(getVisibility, method.fun, getMethodName, returnType,
        Seq(getParameters.asScala), isAddDefaultArgs)

    new ScalaChangeSignatureProcessor(project, changeInfo)
  }

  override def createNorthPanel(): JComponent = {
    val panel = super.createNorthPanel()
    getMethodName match {
      case "apply" | "unapply" | "unapplySeq" | "update" => myNameField.setEnabled(false)
      case _ =>
    }
    panel
  }

  override def validateAndCommitData(): String = {
    val paramItems = parametersTableModel.getItems.asScala
    val problems = ListBuffer[String]()

    if (myReturnTypeCodeFragment != null) {
      if (myReturnTypeCodeFragment.getText.isEmpty)
        problems += RefactoringBundle.message("changeSignature.no.return.type")
      else if (returnTypeText.isEmpty)
        problems += RefactoringBundle.message("changeSignature.wrong.return.type", myReturnTypeCodeFragment.getText)
    }

    val paramNames = paramItems.map(_.parameter.name)
    val names = if (myNameField.isEnabled) getMethodName +: paramNames else paramNames
    problems ++= names.collect {
      case name if !ScalaNamesUtil.isIdentifier(name) => s"$name is not a valid scala identifier"
    }

    val namesWithIndices = paramNames.zipWithIndex
    for {
      (name, idx) <- namesWithIndices
      (name2, idx2) <- namesWithIndices
      if name == name2 && idx < idx2
    } {
      problems += ScalaBundle.message("change.signature.parameters.same.name.{0}", name)
    }
    paramItems.foreach(_.updateType(problems))

    paramItems.foreach {
      case item if item.parameter.isRepeatedParameter && Some(item) != paramItems.lastOption =>
        problems += RefactoringBundle.message("changeSignature.vararg.not.last")
      case _ =>
    }

    if (problems.isEmpty) null
    else problems.distinct.mkString("\n")
  }

  override def createVisibilityControl(): VisibilityPanelBase[String] = new ScalaComboBoxVisibilityPanel(getVisibility)

  override def createParametersInfoModel(method: ScalaMethodDescriptor): ScalaParameterTableModel = {
    new ScalaParameterTableModel(method.fun, method.fun, this)
  }

  override def createReturnTypeCodeFragment(): PsiCodeFragment = {
    val text = method.returnTypeText
    val fragment = new ScalaCodeFragment(project, text)
    HighlightLevelUtil.forceRootHighlighting(fragment, FileHighlightingSetting.SKIP_HIGHLIGHTING)
    fragment.setContext(method.fun.getParent, method.fun)
    fragment
  }

  private def returnTypeAndText: (ScType, String) = {
    if (myReturnTypeCodeFragment == null) return (Any, "")
    try {
      val text = myReturnTypeCodeFragment.getText
      val typeElem = ScalaPsiElementFactory.createTypeElementFromText(text, myReturnTypeCodeFragment.getContext, myReturnTypeCodeFragment)
      (typeElem.getType().getOrAny, typeElem.getText)
    }
    catch {
      case e: Exception => (Any, "")
    }
  }

  def returnType = returnTypeAndText._1

  def returnTypeText = returnTypeAndText._2

  override def isListTableViewSupported: Boolean = true

  def parametersTableModel = myParametersTableModel

  def signatureUpdater = mySignatureUpdater

  override def calculateSignature(): String = {
    def nameAndType(item: ScalaParameterTableModelItem) = {
      if (item.parameter.name == "") ""
      else ScalaExtractMethodUtils.typedName(item.parameter.name, item.typeText, project, byName = false)
    }

    val prefix = method.fun match {
      case fun: ScFunction =>
        val name = if (!fun.isConstructor) getMethodName else "this"
        s"$getVisibility def $name"
      case pc: ScPrimaryConstructor => s"class ${pc.getClassNameText} $getVisibility"
      case _ => ""
    }
    val params = parametersTableModel.getItems.asScala.map(p => p.keywordsAndAnnotations + nameAndType(p))
    val paramsText = params.mkString("(", ", ", ")")

    if (params.exists(_.endsWith("*"))) defaultValuesUsagePanel.forceIsModifyCalls()
    else defaultValuesUsagePanel.release()

    val retTypeText = returnTypeText

    val typeAnnot = if (retTypeText.isEmpty) "" else s": $retTypeText"
    s"$prefix$paramsText$typeAnnot"
  }

  protected override def doValidate(): ValidationInfo = {
    if (!getTableComponent.isEditing) {
      for {
        item <- myParametersTableModel.getItems.asScala
        if item.parameter.oldIndex < 0 && StringUtil.isEmpty(item.defaultValueCodeFragment.getText)
      } {
        val stuff = if (isAddDefaultArgs) "Default arguments" else "Method calls"
        val message = s"Default value is missing. $stuff will contain blanks instead of the new parameter value."
        return new ValidationInfo(message)
      }
    }
    super.doValidate()
  }

  override protected def postponeValidation: Boolean = false
  
  def isAddDefaultArgs = defaultValuesUsagePanel.isAddDefaultArgs

  override def createOptionsPanel(): JComponent = {
    val panel = super.createOptionsPanel() //to initialize fields in base class
    defaultValuesUsagePanel = new DefaultValuesUsagePanel
    panel.add(defaultValuesUsagePanel)
    myPropagateParamChangesButton.setVisible(false)
    panel
  }

  override def dispose(): Unit = {
    parametersTableModel.clear()
    super.dispose()
  }

  override def mayPropagateParameters(): Boolean = false
}
