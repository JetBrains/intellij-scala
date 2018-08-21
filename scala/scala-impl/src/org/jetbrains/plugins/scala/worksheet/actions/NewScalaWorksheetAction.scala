package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext, LangDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.actions.LazyFileTemplateAction
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

class NewScalaWorksheetAction extends LazyFileTemplateAction("Scala Worksheet", Icons.WORKSHEET_LOGO) {
  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = 
    CommonDataKeys.PSI_ELEMENT.getData(dataContext) match {
      case dir: PsiDirectory =>
        suggestDefaultName(dir).map(new AttributesDefaults(_)).orNull
      case _ => null
    }

  override def update(e: AnActionEvent) {
    super.update(e)
    val module: Module = e.getDataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = Option(module).exists(_.hasScala)
    e.getPresentation.setEnabled(isEnabled)
    e.getPresentation.setVisible(isEnabled)
    e.getPresentation.setIcon(Icons.WORKSHEET_LOGO)
  }
  
  private def suggestDefaultName(dir: PsiDirectory): Option[String] = {
    val vFile = dir.getVirtualFile
    val name = "untitled"

    if (vFile.findChild(s"$name.${ScalaFileType.WORKSHEET_EXTENSION}") != null) {
      for (i <- 1 to 1000) if (vFile.findChild(s"$name$i.${ScalaFileType.WORKSHEET_EXTENSION}") == null) return Option(s"$name$i")
      
      return None
    } 

    Option(name)
  }
}