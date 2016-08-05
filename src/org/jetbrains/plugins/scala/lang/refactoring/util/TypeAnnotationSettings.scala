package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, TypeAnnotationRequirement}

/**
  * Created by user on 8/3/16.
  */
object TypeAnnotationSettings {
  def set(project: Project, newSettings: ScalaCodeStyleSettings): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
  
    settings.PRIVATE_METHOD_TYPE_ANNOTATION = newSettings.PRIVATE_METHOD_TYPE_ANNOTATION
    settings.PRIVATE_PROPERTY_TYPE_ANNOTATION = newSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION
    settings.PUBLIC_METHOD_TYPE_ANNOTATION = newSettings.PUBLIC_METHOD_TYPE_ANNOTATION
    settings.PUBLIC_PROPERTY_TYPE_ANNOTATION = newSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    settings.PROTECTED_METHOD_TYPE_ANNOTATION = newSettings.PROTECTED_METHOD_TYPE_ANNOTATION
    settings.PROTECTED_PROPERTY_TYPE_ANNOTATION = newSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION
    settings.LOCAL_METHOD_TYPE_ANNOTATION = newSettings.LOCAL_METHOD_TYPE_ANNOTATION
    settings.LOCAL_PROPERTY_TYPE_ANNOTATION = newSettings.LOCAL_PROPERTY_TYPE_ANNOTATION
    settings.OVERRIDING_METHOD_TYPE_ANNOTATION = newSettings.OVERRIDING_METHOD_TYPE_ANNOTATION
    settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = newSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION
    settings.SIMPLE_METHOD_TYPE_ANNOTATION = newSettings.SIMPLE_METHOD_TYPE_ANNOTATION
    settings.SIMPLE_PROPERTY_TYPE_ANNOTATION = newSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION
  }
  
  def alwaysAddType(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    val coppedSettings = settings.clone().asInstanceOf[ScalaCodeStyleSettings]
    coppedSettings.PRIVATE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.SIMPLE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    coppedSettings
  }

  def noTypeAnnotationForPublic(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings
  }

  def noTypeAnnotationForProtected(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings
  }

  def noTypeAnnotationForOverride(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings
  }

  def noTypeAnnotationForLocal(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings
  }
}
