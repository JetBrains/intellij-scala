package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

private class ExtensionNode(extension: ScExtension)(implicit project: ProjectContext, settings: ViewSettings)
  extends AbstractPsiBasedNode[ScExtension](project, extension, settings) {

  override protected def extractPsiFromValue: PsiElement = getValue

  override protected def getChildrenImpl: util.Collection[Node] =
    Option(getValue).filter(_.isValid).fold(java.util.Collections.emptyList[Node]) { ext =>
      ext.extensionMethods.flatMap(buildMemberNodes(_)(project, settings)).asJava
    }

  override protected def updateImpl(data: PresentationData): Unit =
    Option(getValue).filter(_.isValid).foreach { ext =>
      val parameterTypeText = ext.targetTypeElement.fold("")(_.getText)
      data.setPresentableText(s"extension ($parameterTypeText)")
    }
}