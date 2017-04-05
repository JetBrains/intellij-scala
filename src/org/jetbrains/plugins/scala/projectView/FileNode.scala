package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
private class FileNode(file: ScalaFile)(implicit project: Project, settings: ViewSettings)
  extends PsiFileNode(project, file, settings) {

  override def getChildrenImpl: util.Collection[Node] =
    (settings.isShowMembers && !file.isScriptFile).fold(file.typeDefinitions, Seq.empty)
      .map(new TypeDefinitionNode(_): Node)
      .asJava

  override protected def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)

    val name = Option(file.getVirtualFile).map(_.getNameWithoutExtension).getOrElse(file.getName)
    data.setPresentableText(name)

    val icon = if (file.isScriptFile) Icons.SCRIPT_FILE_LOGO else Icons.FILE
    data.setIcon(icon)
  }
}
