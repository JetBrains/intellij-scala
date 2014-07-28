package org.jetbrains.plugins.scala.actions

import java.util.Properties

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions.{toPsiClassExt, toPsiNamedElementExt}

/**
 * @author Alefas
 * @since 04.05.12
 */
object ScalaFileTemplateUtil {
  val SCALA_IMPLEMENTED_METHOD_TEMPLATE = "Implemented Scala Method Body.scala"
  val SCALA_OVERRIDDEN_METHOD_TEMPLATE = "Overridden Scala Method Body.scala"

  val SCALA_OBJECT = "Scala Object"
  val SCALA_TRAIT = "Scala Trait"
  val SCALA_CLASS = "Scala Class"

  def setClassAndMethodNameProperties(properties: Properties, aClass: PsiClass, method: PsiMethod) {
    var className: String = aClass.qualifiedName
    if (className == null) className = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, className)
    var classSimpleName: String = aClass.name
    if (classSimpleName == null) classSimpleName = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, classSimpleName)
    val methodName: String = method.name
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, methodName)
  }
}
