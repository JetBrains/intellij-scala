package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
trait ScalaTypeAnnotationSettings {
  def isTypeAnnotationRequiredFor(declaration: Declaration,
                                  location: Location,
                                  implementation: Option[Implementation]): Boolean
}

object ScalaTypeAnnotationSettings {
  def apply(project: Project): ScalaTypeAnnotationSettings =
    new TypeAnnotationSettingsImpl(ScalaCodeStyleSettings.getInstance(project))

  private class TypeAnnotationSettingsImpl(style: ScalaCodeStyleSettings) extends ScalaTypeAnnotationSettings {
    override def isTypeAnnotationRequiredFor(declaration: Declaration,
                                             location: Location,
                                             implementation: Option[Implementation]): Boolean = {
      val isLocal = location.isInLocalScope

      import style._

      {
        TYPE_ANNOTATION_IMPLICIT_MODIFIER && declaration.isImplicit ||
          TYPE_ANNOTATION_UNIT_TYPE && declaration.hasUnitType ||
            implementation.exists(_.containsReturn)
      } || {
        if (declaration.entity == Entity.Parameter) TYPE_ANNOTATION_FUNCTION_PARAMETER
        if (declaration.entity == Entity.UnderscoreParameter) TYPE_ANNOTATION_UNDERSCORE_PARAMETER
        else if (isLocal) TYPE_ANNOTATION_LOCAL_DEFINITION
        else declaration.visibility match {
          case Visibility.Private => TYPE_ANNOTATION_PRIVATE_MEMBER
          case Visibility.Protected => TYPE_ANNOTATION_PROTECTED_MEMBER
          case Visibility.Default => TYPE_ANNOTATION_PUBLIC_MEMBER
          case _ => false
        }
      } &&
        ! {
          TYPE_ANNOTATION_EXCLUDE_CONSTANT && declaration.isConstant ||
            TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT && location.isInScript ||
            TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES && location.isInTestSources ||
            !isLocal && TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS && location.isInsidePrivateClass ||
            !isLocal && TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS && location.isInsideAnonymousClass ||
            TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS && implementation.exists(_.isTypeObvious) ||
            !isLocal && location.isInsideOf(TYPE_ANNOTATION_EXCLUDE_MEMBER_OF.asScala.toSet) ||
            declaration.isAnnotatedWith(TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH.asScala.toSet) ||
            declaration.typeMatches(TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES.asScala.toSet)
        }
    }
  }
}
