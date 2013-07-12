package org.jetbrains.sbt
package project

import com.intellij.projectImport.ProjectOpenProcessorBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.openapi.externalSystem.service.project.wizard.SelectExternalProjectStep
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ide.wizard.Step

/**
 * @author Pavel Fatin
 */
class SbtProjectOpenProcessor(builder: SbtProjectImportBuilder) extends ProjectOpenProcessorBase[SbtProjectImportBuilder](builder) {
  def getSupportedExtensions = Array(Sbt.BuildFile)

  override def doQuickImport(file: VirtualFile, wizardContext: WizardContext) = {
    val path = if (file.isDirectory) file.getPath else file.getParent.getPath

    val dialog = new AddModuleWizard(null, path, new SbtProjectImportProvider(getBuilder))

    getBuilder.prepare(wizardContext)
    getBuilder.getControl(null).setLinkedProjectPath(path)

    dialog.getWizardContext.setProjectBuilder(getBuilder)
    dialog.navigateToStep((step: Step) => step.isInstanceOf[SelectExternalProjectStep])

    if (StringUtil.isEmpty(wizardContext.getProjectName)) {
      val projectName = dialog.getWizardContext.getProjectName
      if (!StringUtil.isEmpty(projectName)) {
        wizardContext.setProjectName(projectName)
      }
    }

    dialog.show()

    dialog.isOK
  }
}
