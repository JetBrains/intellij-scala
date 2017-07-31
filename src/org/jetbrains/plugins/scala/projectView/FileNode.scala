package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
private class FileNode(file: ScalaFile)(implicit project: ProjectContext, settings: ViewSettings)
  extends PsiFileNode(project, file, settings) {

  override def getChildrenImpl: util.Collection[Node] =
    (settings.isShowMembers && !file.isScriptFile).fold(file.typeDefinitions, Seq.empty)
      .map(new TypeDefinitionNode(_): Node)
      .asJava

  override protected def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)

    data.setPresentableText(file.getName.stripSuffix(".scala"))

    val icon =
      if (file.isWorksheetFile) Icons.WORKSHEET_LOGO
      else if (file.isScriptFile) Icons.SCRIPT_FILE_LOGO
      else file.getFileType.getIcon

    data.setIcon(icon)
  }
}
