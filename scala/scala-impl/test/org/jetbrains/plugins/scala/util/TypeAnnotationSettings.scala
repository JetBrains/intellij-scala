package org.jetbrains.plugins.scala.util

import java.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
  * Created by user on 8/3/16.
  */
object TypeAnnotationSettings {
  // TODO remove?
  def set(project: Project, newSettings: ScalaCodeStyleSettings): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    import settings._

    TYPE_ANNOTATION_PUBLIC_MEMBER = newSettings.TYPE_ANNOTATION_PUBLIC_MEMBER
    TYPE_ANNOTATION_PROTECTED_MEMBER = newSettings.TYPE_ANNOTATION_PROTECTED_MEMBER
    TYPE_ANNOTATION_PRIVATE_MEMBER = newSettings.TYPE_ANNOTATION_PRIVATE_MEMBER
    TYPE_ANNOTATION_LOCAL_DEFINITION = newSettings.TYPE_ANNOTATION_LOCAL_DEFINITION
    TYPE_ANNOTATION_FUNCTION_PARAMETER = newSettings.TYPE_ANNOTATION_FUNCTION_PARAMETER
    TYPE_ANNOTATION_UNDERSCORE_PARAMETER = newSettings.TYPE_ANNOTATION_UNDERSCORE_PARAMETER

    TYPE_ANNOTATION_IMPLICIT_MODIFIER = newSettings.TYPE_ANNOTATION_IMPLICIT_MODIFIER
    TYPE_ANNOTATION_UNIT_TYPE = newSettings.TYPE_ANNOTATION_UNIT_TYPE
    TYPE_ANNOTATION_STRUCTURAL_TYPE = newSettings.TYPE_ANNOTATION_STRUCTURAL_TYPE

    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS = newSettings.TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS
    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS = newSettings.TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS
    TYPE_ANNOTATION_EXCLUDE_CONSTANT = newSettings.TYPE_ANNOTATION_EXCLUDE_CONSTANT
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS = newSettings.TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS
    TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES = newSettings.TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES
    TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT = newSettings.TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT

    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF = new util.HashSet(newSettings.TYPE_ANNOTATION_EXCLUDE_MEMBER_OF)
    TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH = new util.HashSet(newSettings.TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH)
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = new util.HashSet(newSettings.TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES)
  }
  
  def alwaysAddType(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    val coppedSettings = settings.clone().asInstanceOf[ScalaCodeStyleSettings]

    import coppedSettings._

    TYPE_ANNOTATION_PUBLIC_MEMBER = true
    TYPE_ANNOTATION_PROTECTED_MEMBER = true
    TYPE_ANNOTATION_PRIVATE_MEMBER = true
    TYPE_ANNOTATION_LOCAL_DEFINITION = true
    TYPE_ANNOTATION_FUNCTION_PARAMETER = true
    TYPE_ANNOTATION_UNDERSCORE_PARAMETER = true

    // The above values should be enough
    TYPE_ANNOTATION_IMPLICIT_MODIFIER = false
    TYPE_ANNOTATION_UNIT_TYPE = false
    TYPE_ANNOTATION_STRUCTURAL_TYPE = false

    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS = false
    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS = false
    TYPE_ANNOTATION_EXCLUDE_CONSTANT = false
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS = false
    TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES = false
    TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT = false

    TYPE_ANNOTATION_EXCLUDE_MEMBER_OF = new util.HashSet()
    TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH = new util.HashSet()
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = new util.HashSet()

    coppedSettings
  }

  def noTypeAnnotationForPublic(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.TYPE_ANNOTATION_PUBLIC_MEMBER = false
    settings
  }

  def noTypeAnnotationForProtected(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.TYPE_ANNOTATION_PROTECTED_MEMBER = false
    settings
  }

  def noTypeAnnotationForLocal(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.TYPE_ANNOTATION_LOCAL_DEFINITION = false
    settings
  }
}
