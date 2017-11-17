package org.jetbrains.plugins.scala
package codeInspection.feature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ReferenceTarget, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScExistentialClause, ScRefinement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings

/**
 * @author Pavel Fatin
 */
class LanguageFeatureInspection extends AbstractInspection("LanguageFeature", "Advanced language features"){
  private val Features = Seq(
    Feature("postfix operator notation", "scala.language", "postfixOps", _.postfixOps, _.postfixOps = true) {
      case e: ScPostfixExpr => e.operation
    },
    Feature("reflective call", "scala.language", "reflectiveCalls", _.reflectiveCalls, _.reflectiveCalls = true) {
      case e @ ReferenceTarget(Parent(_: ScRefinement)) => e.getLastChild match {
        case id @ ElementType(ScalaTokenTypes.tIDENTIFIER) => id
        case _ => e
      }
    },
    Feature("dynamic member selection", "scala.language", "dynamics", _.dynamics, _.dynamics = true) {
      case e @ ReferenceTarget(ClassQualifiedName("scala.Dynamic")) && Parent(Parent(Parent(_: ScClassParents))) => e
    },
    Feature("implicit conversion", "scala.language", "implicitConversions", _.implicitConversions, _.implicitConversions = true) {
      case e: ScFunctionDefinition if e.getModifierList.has(ScalaTokenTypes.kIMPLICIT) &&
              e.parameters.size == 1 &&
              !e.parameterList.clauses.exists(_.isImplicit) =>
        Option(e.getModifierList.findFirstChildByType(ScalaTokenTypes.kIMPLICIT)).getOrElse(e)
    },
    Feature("higher-kinded type", "scala.language", "higherKinds", _.higherKinds, _.higherKinds = true) {
      case (e: ScTypeParamClause) && Parent(Parent(_: ScTypeParamClause)) => e
      case (e: ScTypeParamClause) && Parent(_: ScTypeAliasDeclaration) => e
    },
    Feature("existential type", "scala.language", "existentials", _.existentials, _.existentials = true) {
      case e: ScExistentialClause => e.firstChild.getOrElse(e) // TODO Exclude reducible existential types
    },
    Feature("macro definition", "scala.language.experimental", "macros", _.macros, _.macros = true) {
      case e: ScMacroDefinition => e.children.find(it => it.getText == "macro").getOrElse(e)
    })

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = PartialFunction.apply { e: PsiElement =>
    val module = ModuleUtilCore.findModuleForPsiElement(e)

    if (module != null && module.scalaSdk.exists(_.languageLevel >= Scala_2_10)) {
      Features.foreach(_.process(e, holder))
    }
  }
}

private case class Feature(name: String,
                           flagQualifier: String,
                           flagName: String,
                           isEnabled: ScalaCompilerSettings => Boolean,
                           enable: ScalaCompilerSettings => Unit)
                          (findIn: PartialFunction[PsiElement, PsiElement]) {

  def process(e: PsiElement, holder: ProblemsHolder) {
    e.module.foreach { module =>
      if (!isEnabled(module.scalaCompilerSettings)) {
        findIn.lift(e).foreach { it =>
          if (!isFlagImportedFor(it)) {
            holder.registerProblem(it, "Advanced language feature: " + name,
              new ImportFeatureFlagFix(it, name, flagQualifier + "." + flagName),
              new EnableFeatureFix(module.scalaCompilerSettings, it, name, enable))
          }
        }
      }
    }
  }
  
  private def isFlagImportedFor(e: PsiElement): Boolean = {
    ScalaPsiElementFactory.createReferenceFromText(flagName, e, e).resolve() match {
      case e: ScReferencePattern => Option(e.containingClass).exists(_.qualifiedName == flagQualifier)
      case _ => false
    }
  }
}

private class ImportFeatureFlagFix(e: PsiElement, name: String, flag: String)
        extends AbstractFixOnPsiElement("Import feature flag for %ss".format(name), e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    val importsHolder = ScalaImportTypeFix.getImportHolder(elem, elem.getProject)
    importsHolder.addImportForPath(flag, elem)
  }
}

private class EnableFeatureFix(settings: => ScalaCompilerSettings, e: PsiElement, name: String, f: ScalaCompilerSettings => Unit)
        extends AbstractFixOnPsiElement("Enable " + name + "s", e) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    f(settings)
  }
}
