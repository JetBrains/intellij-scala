package org.jetbrains.plugins.scala
package projectView

import java.util
import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass

import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

import scala.jdk.CollectionConverters._

// TODO: create tests for project view!
/**
 * @author Pavel Fatin
 */
private[projectView] class TypeDefinitionNode(definition: ScTypeDefinition)
                                             (implicit project: Project, settings: ViewSettings)
  extends CustomDefinitionNode(definition) {

  override final def getIcon(flags: Int): Icon = definition.getIcon(flags)

  override def getTitle: String =
    validValue.fold(super.getTitle)(_.qualifiedName)

  override def getChildrenImpl: util.Collection[Node] =
    if (settings.isShowMembers)
      validValue.fold(emptyNodesList)(childrenOf)
    else
      super.getChildrenImpl

  private def childrenOf(value: ScTypeDefinition): util.List[Node] = {
    val enumCasesNodes = value match {
      case enum: ScEnum => enum.cases.map(new NamedElementNode(_))
      case _ => Nil
    }

    val memberNodes: Seq[Node] = value.members.flatMap {
      case definition: ScTypeDefinition =>
        Seq(new TypeDefinitionNode(definition))
      case element: ScNamedElement =>
        Seq(new NamedElementNode(element))
      case value: ScValueOrVariable =>
        value.declaredElements.map(new NamedElementNode(_))
      case _ => Seq.empty
    }

    (enumCasesNodes ++ memberNodes).asJava
  }

  override def getPsiClass: PsiClass =
    validValue.filter(_.isObject)
      .fold(super.getPsiClass) { definition =>
        new PsiClassWrapper(definition, definition.qualifiedName, definition.name)
      }
}
