package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
private class TypeDefinitionNode(definition: ScTypeDefinition)(implicit project: ProjectContext, settings: ViewSettings)
  extends ClassTreeNode(project, definition, settings) {

  myName = definition.name

  override def getTitle: String =
    value.map(_.qualifiedName).getOrElse(super.getTitle)

  override def updateImpl(data: PresentationData): Unit = value match {
    case Some(it) => data.setPresentableText(it.name)
    case None => super.updateImpl(data)
  }

  override def getChildrenImpl: util.Collection[Node] =
    if (getSettings.isShowMembers) value.map(childrenOf).getOrElse(Seq.empty).asJava
    else super.getChildrenImpl

  private def childrenOf(parent: ScTypeDefinition): Seq[Node] = parent.members.flatMap {
    case definition: ScTypeDefinition =>
      Seq(new TypeDefinitionNode(definition))

    case element: ScNamedElement =>
      Seq(new NamedElementNode(element))

    case value: ScValue =>
      value.declaredElements.map(new NamedElementNode(_))

    case variable: ScVariable =>
      variable.declaredElements.map(new NamedElementNode(_))

    case _ => Seq.empty
  }

  override def getPsiClass: PsiClass =
    value.filter(_.isObject)
      .map(it => new PsiClassWrapper(it, it.qualifiedName, it.name))
      .getOrElse(super.getPsiClass)

  private def value: Option[ScTypeDefinition] = Some(getValue) collect {
    case value: ScTypeDefinition if value.isValid => value
  }
}
