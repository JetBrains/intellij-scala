package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory

class DottyProjectTemplatesFactory extends ProjectTemplatesFactory {
  override def getGroups: Array[String] = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    val url = getClass.getClassLoader.getResource("projectTemplates/dottyTemplate.zip")
    val templateName = "Dotty (experimental)"
    val description =
      """Minimal Dotty (Scala 3) project <br>
        |Scala 3 support in IntelliJ IDEA is experimental as Scala 3 itself is not stable yet.""".stripMargin

    //todo: try to download latest version of https://github.com/lampepfl/dotty.g8
    Array(ArchivedSbtProjectTemplate(templateName, description, url))
  }
}
