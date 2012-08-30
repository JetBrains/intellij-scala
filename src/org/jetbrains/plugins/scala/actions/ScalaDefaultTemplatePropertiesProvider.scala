package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.{FileTemplate, TemplatePackagePropertyProvider}
import com.intellij.psi.PsiDirectory
import java.util.Properties

/**
 * Pavel Fatin
 */

class ScalaDefaultTemplatePropertiesProvider extends TemplatePackagePropertyProvider {
  private val QualifiedPackagePattern = "(.+)\\.(.+?)".r

  override def fillProperties(directory: PsiDirectory, props: Properties) {
    super.fillProperties(directory, props)

    val (packageQualifier, packageSimpleName) = props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME) match {
      case QualifiedPackagePattern(prefix, suffix) => (prefix, suffix)
      case name =>("", name)
    }

    props.put("PACKAGE_QUALIFIER", packageQualifier)
    props.put("PACKAGE_SIMPLE_NAME", packageSimpleName)
  }
}
