package org.jetbrains.plugins.scala.actions

import java.util.Properties

import com.intellij.ide.fileTemplates.{FileTemplate, TemplatePackagePropertyProvider}
import com.intellij.psi.PsiDirectory

/**
 * Pavel Fatin
 */

class ScalaDefaultTemplatePropertiesProvider extends TemplatePackagePropertyProvider {
  private val QualifiedPackagePattern = "(.+)\\.(.+?)".r

  override def fillProperties(directory: PsiDirectory, props: Properties) {
    super.fillProperties(directory, props)

    val attributePackageName = props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME) match {
      case name: String => name
      case _ => return
    }

    val (packageQualifier, packageSimpleName) = attributePackageName match {
      case QualifiedPackagePattern(prefix, suffix) => (prefix, suffix)
      case name =>("", name)
    }

    props.put("PACKAGE_QUALIFIER", packageQualifier)
    props.put("PACKAGE_SIMPLE_NAME", packageSimpleName)
  }
}
