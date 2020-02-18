package org.jetbrains.plugins.scala
package codeInspection
package feature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.intention.ScalaAddImportAction
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ReferenceTarget, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScExistentialClause, ScRefinement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScTypeAlias, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerSettings, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

/**
 * @author Pavel Fatin
 */
class LanguageFeatureInspection extends AbstractInspection("Advanced language features") {

  private val Features = Seq(
    Feature("postfix operator notation", "scala.language", "postfixOps", _.postfixOps, _.copy(postfixOps = true)) {
      // TODO if !e.applicationProblems.exists(_.isInstanceOf[MissedValueParameter]), see TypeMismatchHighlightingTest
      case e: ScPostfixExpr => e.operation
    },
    Feature("reflective call", "scala.language", "reflectiveCalls", _.reflectiveCalls, _.copy(reflectiveCalls = true)) {
      case e @ ReferenceTarget(decl@Parent(_: ScRefinement)) if !decl.isInstanceOf[ScTypeAlias] => e.getLastChild match {
        case id @ ElementType(ScalaTokenTypes.tIDENTIFIER) => id
        case _ => e
      }
    },
    Feature("dynamic member selection", "scala.language", "dynamics", _.dynamics, _.copy(dynamics = true)) {
      case e@ReferenceTarget(ClassQualifiedName("scala.Dynamic")) && Parent(Parent(Parent(_: ScTemplateParents))) => e
    },
    Feature("implicit conversion", "scala.language", "implicitConversions", _.implicitConversions, _.copy(implicitConversions = true)) {
      case e: ScFunctionDefinition if e.getModifierList.isImplicit &&
              e.parameters.size == 1 &&
              !e.parameterList.clauses.exists(_.isImplicit) =>
        Option(e.getModifierList.findFirstChildByType(ScalaTokenTypes.kIMPLICIT)).getOrElse(e)
    },
    Feature("higher-kinded type", "scala.language", "higherKinds", _.higherKinds, _.copy(higherKinds = true)) {
      case (e: ScTypeParamClause) && Parent(Parent(_: ScTypeParamClause)) => e
      case (e: ScTypeParamClause) && Parent(_: ScTypeAliasDeclaration) => e
    },
    Feature("existential type", "scala.language", "existentials", _.existentials, _.copy(existentials = true)) {
      case e: ScExistentialClause => e.firstChild.getOrElse(e) // TODO Exclude reducible existential types
    },
    Feature("macro definition", "scala.language.experimental", "macros", _.macros, _.copy(macros = true)) {
      case e: ScMacroDefinition => e.children.find(it => it.getText == "macro").getOrElse(e)
    }
  )

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = { case e: PsiElement =>
    val hasScala = e.module.exists(_.hasScala)
    if (hasScala) {
      Features.foreach(_.process(e, holder))
    }
  }
}

private case class Feature(name: String,
                           flagQualifier: String,
                           flagName: String,
                           isEnabled: ScalaCompilerSettings => Boolean,
                           enable: ScalaCompilerSettings => ScalaCompilerSettings)
                          (findIn: PartialFunction[PsiElement, PsiElement]) {

  def process(e: PsiElement, holder: ProblemsHolder): Unit = {
    compilerProfile(e).foreach { profile =>
      if (!isEnabled(profile.getSettings)) {
        findIn.lift(e).foreach { it =>
          if (!isFlagImportedFor(it)) {
            holder.registerProblem(it, s"Advanced language feature: $name",
              new ImportFeatureFlagFix(it, name, s"$flagQualifier.$flagName"),
              new EnableFeatureFix(profile, it, name, enable))
          }
        }
      }
    }
  }

  private def compilerProfile(e: PsiElement): Option[ScalaCompilerSettingsProfile] =
    e.getContainingFile match {
      case null                                    => None
      case file: ScalaFile if file.isWorksheetFile => Option(WorksheetFileSettings(file).getCompilerProfile)
      case file                                    => file.module.map(_.scalaCompilerSettingsProfile)
    }

  private def isFlagImportedFor(e: PsiElement): Boolean = {
    val reference = ScalaPsiElementFactory.createReferenceFromText(flagName, e, e)
    reference.resolve() match {
      case e: ScReferencePattern => Option(e.containingClass).exists(_.qualifiedName == flagQualifier)
      case _                     => false
    }
  }
}

private class ImportFeatureFlagFix(e: PsiElement, name: String, flag: String)
        extends AbstractFixOnPsiElement("Import feature flag for %ss".format(name), e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    val importsHolder = ScalaAddImportAction.getImportHolder(elem, elem.getProject)
    importsHolder.addImportForPath(flag, elem)
  }
}

private class EnableFeatureFix(profile: => ScalaCompilerSettingsProfile,
                               e: PsiElement,
                               name: String,
                               update: ScalaCompilerSettings => ScalaCompilerSettings)
        extends AbstractFixOnPsiElement("Enable " + name + "s", e) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    val updatedSettings = update(profile.getSettings)
    profile.setSettings(updatedSettings)
  }
}
