package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.ProjectContext

private class PackageObjectNode(definition: ScTypeDefinition)(implicit project: ProjectContext, settings: ViewSettings)
  extends TypeDefinitionNode(definition) {

  override def getTypeSortWeight(sortByType: Boolean): Int = 4 // PsiDirectoryNode.getTypeSortWeight == 3

  override def update(data: PresentationData): Unit = {
    super.update(data)

    data.clearText()
    Option(definition.getContainingFile).foreach { file =>
      data.addText(getNameWithoutExtension(file.getName) + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    data.addText(definition.name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
