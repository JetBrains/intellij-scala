package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.util.ElementPresentationUtilScala

import java.util
import javax.swing.Icon

final class ScalaCompanionsFileNode(
  project: Project,
  delegate: ScTypeDefinition,
  settings: ViewSettings,
) extends PsiFileNode(
  project,
  delegate.getContainingFile,
  settings
) with IconableNode {

  override def toTestString(ignoredPrintInfo: Queryable.PrintInfo): String = getTestPresentation

  //noinspection ScalaDeprecation
  //This method is deprecated but still is used in project view tests
  //(com.intellij.projectView.BaseProjectViewTestCase)
  override def getTestPresentation: String = {
    val kind = delegate match {
      case _: ScClass => "class"
      case _: ScTrait => "trait"
      case _: ScObject => "object"
      case _: ScEnum => "enum"
    }
    s"ScalaCompanionsFileNode: $kind ${delegate.name}"
  }

  override def getIcon(flags: Int): Icon = {
    val icon = baseCompanionIcon
    val layerFlags = ElementPresentationUtilScala.getBaseLayerFlags(delegate, flags)
    ElementPresentationUtilScala.getIconWithLayeredFlags(delegate, flags, icon, layerFlags)
  }

  private def baseCompanionIcon: Icon = delegate match {
    case _: ScTrait                          => Icons.TRAIT_AND_OBJECT
    case _: ScEnum                           => Icons.ENUM_AND_OBJECT
    case c: ScClass if c.hasAbstractModifier => Icons.ABSTRACT_CLASS_AND_OBJECT
    case _                                   => Icons.CLASS_AND_OBJECT
  }

  override def isAlwaysLeaf: Boolean = true

  //noinspection TypeAnnotation
  override def getChildrenImpl: util.List[Node] = emptyNodesList

  override def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    setIcon(data)
    data.setPresentableText(delegate.name)
  }
}