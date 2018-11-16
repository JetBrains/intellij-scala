package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private class PackageObjectNode(definition: ScTypeDefinition)(implicit project: ProjectContext, settings: ViewSettings)
  extends TypeDefinitionNode(definition) {

  override def getTypeSortWeight(sortByType: Boolean): Int =
    if (groupWithPackage) 2 else 4 // PsiDirectoryNode.getTypeSortWeight == 3

  private def groupWithPackage: Boolean =
    ScalaProjectSettings.getInstance(myProject).isGroupPackageObjectWithPackage

  override def update(data: PresentationData): Unit = {
    super.update(data)

    if (!groupWithPackage) {
      data.clearText()
      Option(definition.getContainingFile).foreach { file =>
        data.addText(getNameWithoutExtension(file.getName) + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
  }
}
