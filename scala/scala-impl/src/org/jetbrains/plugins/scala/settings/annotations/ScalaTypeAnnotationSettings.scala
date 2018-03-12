package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
trait ScalaTypeAnnotationSettings {
  def isTypeAnnotationRequiredFor(declaration: Declaration, location: Location, implementation: Option[Implementation]): Boolean =
    reasonForTypeAnnotationOn(declaration, location, implementation).isDefined

  def reasonForTypeAnnotationOn(declaration: Declaration, location: Location, implementation: Option[Implementation]): Option[String]
}

object ScalaTypeAnnotationSettings {
  def apply(project: Project): ScalaTypeAnnotationSettings =
    new TypeAnnotationSettingsImpl(ScalaCodeStyleSettings.getInstance(project))

  private class TypeAnnotationSettingsImpl(style: ScalaCodeStyleSettings) extends ScalaTypeAnnotationSettings {
    override def reasonForTypeAnnotationOn(declaration: Declaration, location: Location, implementation: Option[Implementation]): Option[String] = {
      val entity = declaration.entity
      val isLocal = location.isInLocalScope

      import style._

      def reasonToEnforce =
        (TYPE_ANNOTATION_IMPLICIT_MODIFIER && !entity.isParameter && declaration.isImplicit).option("implicit definition")
          .orElse((TYPE_ANNOTATION_UNIT_TYPE && !entity.isParameter && declaration.hasUnitType).option("Unit definition"))
          .orElse((entity == Entity.Method && implementation.exists(_.containsReturn)).option("method with 'return'"))
          .orElse((TYPE_ANNOTATION_STRUCTURAL_TYPE && !entity.isParameter && declaration.hasAccidentalStructuralType).option("structural type definition"))

      def reasonToUse = entity match {
        case Entity.Parameter => TYPE_ANNOTATION_FUNCTION_PARAMETER.option("function literal parameter")
        case Entity.UnderscoreParameter => TYPE_ANNOTATION_UNDERSCORE_PARAMETER.option("underscore parameter")
        case _ =>
          if (isLocal) TYPE_ANNOTATION_LOCAL_DEFINITION.option("local definition")
          else declaration.visibility match {
            case Visibility.Private => TYPE_ANNOTATION_PRIVATE_MEMBER.option("private member")
            case Visibility.Protected => TYPE_ANNOTATION_PROTECTED_MEMBER.option("protected member")
            case Visibility.Default => TYPE_ANNOTATION_PUBLIC_MEMBER.option("public member")
          }
      }

      def isExcluded =
        TYPE_ANNOTATION_EXCLUDE_CONSTANT && declaration.isConstant ||
        TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT && location.isInScript ||
        TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES && location.isInTestSources ||
        TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS && !isLocal && location.isInsidePrivateClass ||
        TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS && !isLocal && location.isInsideAnonymousClass ||
        TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS && implementation.exists(_.isTypeObvious) ||
        !isLocal && location.isInsideOf(TYPE_ANNOTATION_EXCLUDE_MEMBER_OF.asScala.toSet) ||
        declaration.isAnnotatedWith(TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH.asScala.toSet) ||
        declaration.typeMatches(TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES.asScala.toSet)

      reasonToEnforce.orElse(reasonToUse.filterNot(_ => isExcluded))
    }
  }
}
