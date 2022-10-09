package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.Iconable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import java.util
import javax.swing.Icon

final class ScalaCompanionsFileNode(
  project: Project,
  delegate: ScTypeDefinition,
  settings: ViewSettings,
  iconProvider: Iconable
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

  override def getIcon(flags: Int): Icon = iconProvider.getIcon(flags)

  override def isAlwaysLeaf: Boolean = true

  //noinspection TypeAnnotation
  override def getChildrenImpl: util.List[Node] = emptyNodesList

  override def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    setIcon(data)
    data.setPresentableText(delegate.name)
  }
}