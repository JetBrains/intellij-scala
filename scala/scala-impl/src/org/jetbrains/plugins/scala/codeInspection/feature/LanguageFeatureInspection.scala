package org.jetbrains.plugins.scala.codeInspection.feature

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ReferenceTarget, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScExistentialClause, ScRefinement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScTypeAlias, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings.scalaVersionSinceWhichHigherKindsAreAlwaysEnabled
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerSettings, ScalaCompilerSettingsProfile}

class LanguageFeatureInspection extends LocalInspectionTool {

  private val Features = Seq(
    Feature(ScalaInspectionBundle.message("language.feature.postfix.operator.notation"), "scala.language", "postfixOps", _.postfixOps, _.copy(postfixOps = true),
      isErrorOn = _.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_2_13) {
      // TODO if !e.applicationProblems.exists(_.isInstanceOf[MissedValueParameter]), see TypeMismatchHighlightingTest
      case e: ScPostfixExpr => e.operation
    },
    Feature(ScalaInspectionBundle.message("language.feature.reflective.call"), "scala.language", "reflectiveCalls", _.reflectiveCalls, _.copy(reflectiveCalls = true)) {
      case e@ReferenceTarget(decl@Parent(_: ScRefinement)) if !decl.is[ScTypeAlias] => e.getLastChild match {
        case id@ElementType(ScalaTokenTypes.tIDENTIFIER) => id
        case _ => e
      }
    },
    Feature(ScalaInspectionBundle.message("language.feature.dynamic.member.selection"), "scala.language", "dynamics", _.dynamics, _.copy(dynamics = true)) {
      case e@ReferenceTarget(ClassQualifiedName("scala.Dynamic")) & Parent(Parent(Parent(_: ScTemplateParents))) => e
    },
    Feature(ScalaInspectionBundle.message("language.feature.implicit.conversion"), "scala.language", "implicitConversions", _.implicitConversions, _.copy(implicitConversions = true)) {
      case e: ScFunctionDefinition if e.getModifierList.isImplicit &&
        e.parameters.size == 1 &&
        !e.parameterList.clauses.exists(_.isImplicit) =>
        e.getModifierList.findFirstChildByType(ScalaTokenTypes.kIMPLICIT).getOrElse(e)
    },
    Feature(ScalaInspectionBundle.message("language.feature.higher.kinded.type"), "scala.language", "higherKinds", _.higherKinds, _.copy(higherKinds = true),
      isEnabledOn = _.scalaMinorVersionOrDefault < scalaVersionSinceWhichHigherKindsAreAlwaysEnabled) {
      case (e: ScTypeParamClause) & Parent(Parent(_: ScTypeParamClause)) => e
      case (e: ScTypeParamClause) & Parent(_: ScTypeAliasDeclaration) => e
    },
    Feature(ScalaInspectionBundle.message("language.feature.existential.type"), "scala.language", "existentials", _.existentials, _.copy(existentials = true)) {
      case e: ScExistentialClause => e.firstChild.getOrElse(e) // TODO Exclude reducible existential types
    },
    Feature(ScalaInspectionBundle.message("language.feature.macro.definition"), "scala.language.experimental", "macros", _.macros, _.copy(macros = true)) {
      case e: ScMacroDefinition => e.children.find(it => it.textMatches("macro")).getOrElse(e)
    }
  )

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case e: PsiElement =>
      val hasScala = e.module.exists(_.hasScala)
      if (hasScala) {
        Features.foreach(_.process(e, holder))
      }
    case _ =>
  }
}

private case class Feature(@Nls name: String,
                           flagQualifier: String,
                           flagName: String,
                           isEnabled: ScalaCompilerSettings => Boolean,
                           enable: ScalaCompilerSettings => ScalaCompilerSettings,
                           isEnabledOn: PsiElement => Boolean = _ => true,
                           isErrorOn: PsiElement => Boolean = _ => false)
                          (findIn: PartialFunction[PsiElement, PsiElement]) {

  def process(e: PsiElement, holder: ProblemsHolder): Unit = {
    compilerProfile(e).foreach { profile =>
      val compilerSettings = profile.getSettings
      val isFeatureEnabled = isEnabled(compilerSettings) || compilerSettings.languageWildcard
      if (!isFeatureEnabled && isEnabledOn(e)) {
        findIn.lift(e).foreach { it =>
          if (!isFlagImportedFor(it)) {
            holder.registerProblem(
              it,
              ScalaInspectionBundle.message("advanced.language.feature", name),
              if (isErrorOn(e)) ProblemHighlightType.ERROR else ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              new ImportFeatureFlagFix(it, name, s"$flagQualifier.$flagName")
            )
          }
        }
      }
    }
  }

  private def compilerProfile(e: PsiElement): Option[ScalaCompilerSettingsProfile] =
    e.getContainingFile match {
      case null => None
      case file =>
        val provided = ScalaCompilerSettingsProfileProvider.settingsFor(file)
        provided.orElse(file.module.map(_.scalaCompilerSettingsProfile))
    }

  private def isFlagImportedFor(e: PsiElement): Boolean = {
    val reference = ScalaPsiElementFactory.createReferenceFromText(flagName, e, e)
    reference.resolve() match {
      case e: ScReferencePattern => Option(e.containingClass).exists(_.qualifiedName == flagQualifier)
      case _ => false
    }
  }
}

private final class ImportFeatureFlagFix(e: PsiElement, name: String, flag: String)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("import.feature.flag.for.language.feature", name), e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    ScImportsHolder(elem).addImportForPath(flag, elem)
  }
}