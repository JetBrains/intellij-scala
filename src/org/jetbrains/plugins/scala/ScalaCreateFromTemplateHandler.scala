package org.jetbrains.plugins.scala

import com.intellij.ide.fileTemplates.{FileTemplate, CreateFromTemplateHandler}
import com.intellij.openapi.project.Project
import java.lang.String
import java.util.Properties
import com.intellij.psi.{PsiElement, PsiDirectory}

/**
 * Pavel Fatin
 */

// A stub to avoid CreateFromTemplateGroup pollution by Scala plugin's internal templates
// (SCL-2799 Hide plugin's internal templates in "New" menu)
class ScalaCreateFromTemplateHandler extends CreateFromTemplateHandler {
  def handlesTemplate(template: FileTemplate): Boolean =
    ScalaFileType.DEFAULT_EXTENSION.equalsIgnoreCase(template.getExtension) && !template.getName.contains("Script")

  def createFromTemplate(project: Project, directory: PsiDirectory, fileName: String,
                         template: FileTemplate, templateText: String, props: Properties): PsiElement = null

  def canCreate(dirs: Array[PsiDirectory]): Boolean = false
}