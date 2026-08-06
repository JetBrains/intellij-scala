package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.Nls

import javax.swing.Icon

/**
 * @param templateName name of the file template<br>
 *                     It's defined in the plugin.xml file with the tag {{{<internalFileTemplate ... />}}}
 *                     The actual template file full name includes extension `.scala.ft,`
 *                     For example, `Scala CaseObject.scala.ft`.
 */
abstract class LazyFileTemplateAction(
  templateName: String,
  @Nls title: String,
  @Nls description: String,
  val icon: Icon
) extends CreateFromTemplateActionBase(
  title,
  description,
  icon
) with DumbAware {

  private lazy val template = FileTemplateManager.getDefaultInstance.getInternalTemplate(templateName)

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = template
}
