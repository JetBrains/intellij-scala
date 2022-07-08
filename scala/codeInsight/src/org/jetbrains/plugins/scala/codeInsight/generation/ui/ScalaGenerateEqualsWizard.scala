package org.jetbrains.plugins.scala
package codeInsight
package generation
package ui

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.generation.ui.AbstractGenerateEqualsWizard
import com.intellij.openapi.project.Project
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.util.containers.HashMap
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

final class ScalaGenerateEqualsWizard(clazz: ScClass, needEquals: Boolean, needHashCode: Boolean)
                                     (implicit project: Project)
  extends AbstractGenerateEqualsWizard[ScClass, ScNamedElement, ScalaMemberInfo](project,
    new ScalaGenerateEqualsWizard.Builder(clazz, needEquals, needHashCode)) {

  import ScalaGenerateEqualsWizard._

  def equalsFields: Iterable[ScNamedElement] = selectedFields(myEqualsPanel)

  def hashCodeFields: Iterable[ScNamedElement] = selectedFields(myHashCodePanel)
}

object ScalaGenerateEqualsWizard {

  private class Builder(override protected val getPsiClass: ScClass,
                        needEquals: Boolean, needHashCode: Boolean)
    extends AbstractGenerateEqualsWizard.Builder[ScClass, ScNamedElement, ScalaMemberInfo] {

    import Builder._

    override protected val getClassFields: util.List[ScalaMemberInfo] = {
      extractFields(!isVar(_)).map(_._1).asJava
    }

    override protected val getEqualsPanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] =
      if (needEquals) new ScalaMemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.equals.fields.chooser.title"), getClassFields)(ScalaEqualsMemberInfoModel)
      else null

    override protected val getFieldsToHashCode: HashMap[ScNamedElement, ScalaMemberInfo] @nowarn("cat=deprecation") =
      if (needEquals && needHashCode) {
        val result = new HashMap[ScNamedElement, ScalaMemberInfo]: @nowarn("cat=deprecation")
        for {
          (info, member) <- extractFields(Function.const(true))
        } result.put(member, info)
        result
      } else null

    override protected val getHashCodePanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] =
      if (needHashCode) {
        val classFields = getClassFields match {
          case fields if needEquals => updateInfos(fields)
          case fields => fields
        }
        new ScalaMemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.hashcode.fields.chooser.title"), classFields)(ScalaHashCodeMemberInfoModel)
      }
      else null

    override protected def updateHashCodeMemberInfos(equalsMemberInfos: util.Collection[_ <: ScalaMemberInfo]): Unit =
      getHashCodePanel match {
        case null =>
        case panel => panel.getTable.setMemberInfos(updateInfos(equalsMemberInfos))
      }

    override protected def getFieldsToNonNull: HashMap[ScNamedElement, ScalaMemberInfo] @nowarn("cat=deprecation") = null

    override protected def getNonNullPanel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] = null

    override protected def updateNonNullMemberInfos(equalsMemberInfos: util.Collection[_ <: ScalaMemberInfo]): Unit = {}

    private def extractFields(visibility: ScNamedElement => Boolean) =
      for {
        field <- fields(getPsiClass)
        info = new ScalaMemberInfo(field)
        member = info.getMember
      } yield {
        info.setChecked(visibility(member))
        (info, member)
      }

    private def updateInfos(infos: util.Collection[_ <: ScalaMemberInfo]) = {
      infos.asScala.toList.map { info =>
        getFieldsToHashCode.get(info.getMember)
      }.asJava
    }

  }

  private object Builder {

    private object ScalaEqualsMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]

    private object ScalaHashCodeMemberInfoModel extends AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]

  }

  private def selectedFields(panel: AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo]) =
    panel match {
      case scalaPanel: ScalaMemberSelectionPanel => scalaPanel.members
      case _ => Iterable.empty
    }

}
