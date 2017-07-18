package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.TypeAnnotationRequirement.{Optional, Required}

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
    settings.IMPLICIT_METHOD_TYPE_ANNOTATION = newSettings.IMPLICIT_METHOD_TYPE_ANNOTATION
    settings.IMPLICIT_PROPERTY_TYPE_ANNOTATION = newSettings.IMPLICIT_PROPERTY_TYPE_ANNOTATION
  }
  
  def alwaysAddType(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    val coppedSettings = settings.clone().asInstanceOf[ScalaCodeStyleSettings]
    val ordinal = Required.ordinal

    coppedSettings.PRIVATE_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION = ordinal
    coppedSettings.PUBLIC_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION = ordinal
    coppedSettings.PROTECTED_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION = ordinal
    coppedSettings.LOCAL_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.LOCAL_PROPERTY_TYPE_ANNOTATION = ordinal
    coppedSettings.OVERRIDING_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = ordinal
    coppedSettings.SIMPLE_METHOD_TYPE_ANNOTATION = ordinal
    coppedSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION = ordinal
    settings.IMPLICIT_METHOD_TYPE_ANNOTATION = ordinal
    settings.IMPLICIT_PROPERTY_TYPE_ANNOTATION = ordinal

    coppedSettings
  }

  def noTypeAnnotationForPublic(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.PUBLIC_METHOD_TYPE_ANNOTATION = Optional.ordinal
    settings.PUBLIC_PROPERTY_TYPE_ANNOTATION = Optional.ordinal
    settings
  }

  def noTypeAnnotationForProtected(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.PROTECTED_METHOD_TYPE_ANNOTATION = Optional.ordinal
    settings.PROTECTED_PROPERTY_TYPE_ANNOTATION = Optional.ordinal
    settings
  }

  def noTypeAnnotationForOverride(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.OVERRIDING_METHOD_TYPE_ANNOTATION = Optional.ordinal
    settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = Optional.ordinal
    settings
  }

  def noTypeAnnotationForLocal(settings: ScalaCodeStyleSettings): ScalaCodeStyleSettings ={
    settings.LOCAL_METHOD_TYPE_ANNOTATION = Optional.ordinal
    settings.LOCAL_PROPERTY_TYPE_ANNOTATION = Optional.ordinal
    settings
  }
}
