package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import java.util
import java.util.Collections

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.generation.ui.AbstractGenerateEqualsWizard
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.util.containers.HashMap
import org.jetbrains.plugins.scala.codeInsight.generation.GenerationUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 8/20/13
 */
class ScalaGenerateEqualsWizard(project: Project, aClass: PsiClass, needEquals: Boolean, needHashCode: Boolean)
        extends {private val builder = new ScalaGenerateEqualsWizardBuilder(aClass, needEquals, needHashCode)}
        with AbstractGenerateEqualsWizard[PsiClass, ScNamedElement, ScalaMemberInfo](project, builder) {


  private def getSelectedFields(panel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo]): Seq[ScNamedElement] =
    if(panel == null) Seq.empty
    else panel.getTable.getSelectedMemberInfos.asScala.map(_.getMember).toSeq

  def getEqualsFields: Seq[ScNamedElement] = getSelectedFields(myEqualsPanel)
  def getHashCodeFields: Seq[ScNamedElement] = getSelectedFields(myHashCodePanel)
}

private class ScalaGenerateEqualsWizardBuilder(aClass: PsiClass, needEquals: Boolean, needHashCode: Boolean)
        extends AbstractGenerateEqualsWizard.Builder[PsiClass, ScNamedElement, ScalaMemberInfo] {

  protected def getPsiClass: PsiClass = aClass

  private val classFields: util.List[ScalaMemberInfo] = extractFields
  classFields.forEach {info =>
    if (GenerationUtil.isVar(info.getMember)) info.setChecked(false)
    else info.setChecked(true)
  }

  private val equalsPanel: ScalaMemberSelectionPanel =
    if (needEquals) {
    val panel = new ScalaMemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.equals.fields.chooser.title"), classFields, null)
    panel.getTable.setMemberInfoModel(new ScalaEqualsMemberInfoModel)
    panel
  }
  else null

  private val fieldsToHashCode = if (needHashCode && needEquals) createFieldToMemberInfoMap(checkedByDefault = true) else null
  private val hashCodeMemberInfos =
    if (needHashCode && needEquals) Collections.emptyList[ScalaMemberInfo]
    else if (needHashCode) classFields
    else null
  private val hashCodePanel =
  if (needHashCode) {
    val title = CodeInsightBundle.message("generate.equals.hashcode.hashcode.fields.chooser.title")
    val panel = new ScalaMemberSelectionPanel(title, hashCodeMemberInfos, null)
    panel.getTable.setMemberInfoModel(new ScalaHashCodeMemberInfoModel)
    if (needEquals) updateHashCodeMemberInfos(classFields)
    panel
  }
  else null

  protected def getClassFields: util.List[ScalaMemberInfo] = classFields
  protected def getFieldsToHashCode: HashMap[ScNamedElement, ScalaMemberInfo] = fieldsToHashCode
  protected def getEqualsPanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] = equalsPanel
  protected def getHashCodePanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] = hashCodePanel

  protected def updateHashCodeMemberInfos(equalsMemberInfos: util.Collection[ScalaMemberInfo]) {
    if (hashCodePanel == null) return
    val hashCodeFields = equalsMemberInfos.asScala.map(_.getMember).map(fieldsToHashCode.get(_))
    hashCodePanel.getTable.setMemberInfos(hashCodeFields.asJavaCollection)
  }

  protected def extractFields: util.List[ScalaMemberInfo] = {
    GenerationUtil.getAllFields(aClass).map(new ScalaMemberInfo(_)).asJava
  }

  private def createFieldToMemberInfoMap(checkedByDefault: Boolean): HashMap[ScNamedElement, ScalaMemberInfo] = {
    val memberInfos: util.Collection[ScalaMemberInfo] = extractFields
    val result: HashMap[ScNamedElement, ScalaMemberInfo] = new HashMap[ScNamedElement, ScalaMemberInfo]
    for (memberInfo <- memberInfos.asScala) {
      memberInfo.setChecked(checkedByDefault)
      result.put(memberInfo.getMember, memberInfo)
    }
    result
  }

  protected def getFieldsToNonNull: HashMap[ScNamedElement, ScalaMemberInfo] = null
  protected def getNonNullPanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] = null
  protected def updateNonNullMemberInfos(equalsMemberInfos: util.Collection[ScalaMemberInfo]) {}
}

private class ScalaHashCodeMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]

private class ScalaEqualsMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]
