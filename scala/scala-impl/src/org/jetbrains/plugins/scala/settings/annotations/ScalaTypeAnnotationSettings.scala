package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.settings.annotations.ScalaTypeAnnotationSettings.TypeAnnotationReasons

import scala.jdk.CollectionConverters._

trait ScalaTypeAnnotationSettings {

  def isTypeAnnotationRequiredFor(
    declaration: Declaration,
    location: Location,
    implementation: Option[Implementation] = None
  ): Boolean = {
    val reason = reasonForTypeAnnotationOnImpl(declaration, location, implementation)
    reason.exists(_.reasonToExclude.isEmpty)
  }

  final def reasonForTypeAnnotationOn(
    declaration: Declaration,
    location: Location,
    implementation: Option[Implementation]
  ): Option[String] = {
    val reasons = reasonForTypeAnnotationOnImpl(declaration, location, implementation)
    reasons.filter(_.reasonToExclude.isEmpty).map(_.reasonToEnforceOrUse)
  }

  def reasonForTypeAnnotationOnImpl(
    declaration: Declaration,
    location: Location,
    implementation: Option[Implementation]
  ): Option[TypeAnnotationReasons]
}

object ScalaTypeAnnotationSettings {

  def apply(project: Project): ScalaTypeAnnotationSettings =
    new TypeAnnotationSettingsImpl(ScalaCodeStyleSettings.getInstance(project))

  private[annotations] case class TypeAnnotationReasons(reasonToEnforceOrUse: String, reasonToExclude: Option[String])

  private class TypeAnnotationSettingsImpl(style: ScalaCodeStyleSettings) extends ScalaTypeAnnotationSettings {

    override def reasonForTypeAnnotationOnImpl(
      declaration: Declaration,
      location: Location,
      implementation: Option[Implementation]
    ): Option[TypeAnnotationReasons] = {
      val entity = declaration.entity
      val isLocal = location.isInLocalScope

      import style._

      lazy val reasonToEnforce: Option[String] =
        (TYPE_ANNOTATION_IMPLICIT_MODIFIER && !entity.isParameter && declaration.isImplicit).option("implicit definition")
          .orElse((TYPE_ANNOTATION_UNIT_TYPE && !entity.isParameter && declaration.hasUnitType).option("Unit definition"))
          .orElse((entity == Entity.Method && implementation.exists(_.containsReturn)).option("method with 'return'"))
          .orElse((TYPE_ANNOTATION_STRUCTURAL_TYPE && !entity.isParameter && declaration.hasAccidentalStructuralType).option("structural type definition"))
          .orElse((!entity.isParameter && declaration.isAbstractOrReturnsNullOrThrows).option("abstract or returns null"))

      lazy val reasonToUse: Option[String] = entity match {
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

      lazy val reasonToExclude: Option[String] =
        (TYPE_ANNOTATION_EXCLUDE_CONSTANT && declaration.isConstant).option("constant")
          .orElse(location.isInCodeFragment.option("in code fragment"))
          .orElse((TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES && location.isInDialectSources).option("in dialect source"))
          .orElse((TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES && location.isInTestSources).option("in test source"))
          .orElse((TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS && !isLocal && location.isInsidePrivateClass).option("member of private class"))
          .orElse((TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS && !isLocal && location.isInsideAnonymousClass).option("member of anonymous class"))
          .orElse((TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE && implementation.exists(_.hasStableType)).option("type is stable"))
          .orElse((!isLocal && location.isInsideOf(TYPE_ANNOTATION_EXCLUDE_MEMBER_OF.asScala)).option("is in 'exclude member' set"))
          .orElse(declaration.isAnnotatedWith(TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH.asScala).option("is in 'exclude annotated with' set"))
          .orElse(declaration.typeMatches(TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES.asScala).option("is in 'exclude when type matches' set"))

      val _reasonToEnforce = reasonToEnforce.map(r => TypeAnnotationReasons(r, None))
      _reasonToEnforce.orElse {
        reasonToUse.map(r => TypeAnnotationReasons(r, reasonToExclude))
      }
    }
  }
}
