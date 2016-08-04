package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, TypeAnnotationRequirement}

/**
  * Created by user on 8/3/16.
  */
object TypeAnnotationSettings {
  def alwaysAddType(project: Project): Unit = {
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    settings.PRIVATE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.PRIVATE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.SIMPLE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
    settings.SIMPLE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Required.ordinal
  }

  def noTypeAnnotationForPublic(project: Project): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    settings.PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
  }

  def noTypeAnnotationForProtected(project: Project): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    settings.PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
  }

  def noTypeAnnotationForOverride(project: Project): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    settings.OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
  }

  def noTypeAnnotationForLocal(project: Project): Unit ={
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    settings.LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
    settings.LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal
  }
}
