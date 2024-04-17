package org.jetbrains.plugins.scala.codeInspection.source3

import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.{checkbox, pane}
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.source3.Source3Inspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScNamingPattern, ScSeqWildcardPattern, ScTypePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScWildcardTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import scala.beans.BeanProperty

class Source3Inspection extends LocalInspectionTool {

  @BeanProperty final var convertWildcardUnderscore: Boolean = true
  @BeanProperty final var addGeneratorCase: Boolean = true
  @BeanProperty final var convertWildcardImport: Boolean = true
  @BeanProperty final var convertImportAlias: Boolean = true
  @BeanProperty final var convertVarArgSplices: Boolean = true
  @BeanProperty final var convertNamedWildcardPattern: Boolean = true
  @BeanProperty final var convertCompoundTypes: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
    if (element.getContainingFile.isSource3OrSource3CrossEnabled) {

      lazy val scala3ImportsAllowed = !ScalaCodeStyleSettings.getInstance(element.getProject).isForceScala2ImportSyntaxInSource3()

      val features = element.features

      element match {
        case ScWildcardTypeElementUnderscore(wildcardTypeElement, underscore) if convertWildcardUnderscore && features.`? as wildcard marker` =>
          holder.registerProblem(
            underscore,
            getDisplayName,
            createReplacingQuickFix(wildcardTypeElement, ScalaInspectionBundle.message("replace.with.questionmark")) { e =>
              ScalaPsiElementFactory.createTypeElementFromText(e.getText.replaceFirst("_", "?"), e, null)
            }
          )
        case gen@ScGenerator(pattern, _) if addGeneratorCase && features.`case in pattern bindings` && gen.caseKeyword.isEmpty &&
          generatorType(gen).exists(ty => !pattern.isIrrefutableFor(Some(ty))) =>
          holder.registerProblem(
            pattern,
            getDisplayName,
            createReplacingQuickFix(gen, ScalaInspectionBundle.message("add.case")) { gen =>
              ScalaPsiElementFactory.createExpressionFromText(s"for { case ${gen.getText} } ()", gen)(gen)
                .asInstanceOf[ScFor].enumerators.head.generators.head
            }
          )
        case ElementType(ScalaTokenTypes.tUNDER) if scala3ImportsAllowed && convertWildcardImport && isUpgradableImportWildcard(element) =>
          holder.registerProblem(
            element,
            getDisplayName,
            createReplacingQuickFix(element, ScalaInspectionBundle.message("replace.with.star")) { underscore =>
              ScalaPsiElementFactory.createImportFromText("import a.*", underscore.getContext, null).lastLeaf
            }
          )
        case ElementType(ScalaTokenTypes.tFUNTYPE) if scala3ImportsAllowed && convertImportAlias && features.`Scala 3 renaming imports` && element.getParent.is[ScImportSelector] =>
          holder.registerProblem(
            element,
            getDisplayName,
            createReplacingQuickFix(element, ScalaInspectionBundle.message("replace.with.as")) { arrow =>
              ScalaPsiElementFactory.createImportFromText("import a.{x as y}", arrow.getContext, null)
                .importExprs.head.selectors.head.findFirstChildByType(ScalaTokenType.AsKeyword).get
            }
          )
        case typed@ScTypedExpression.sequenceArg(seqArg) if convertVarArgSplices && features.`Scala 3 vararg splice syntax` &&
          seqArg.getFirstChild.elementType == ScalaTokenTypes.tUNDER =>
          holder.registerProblem(
            seqArg,
            getDisplayName,
            createReplacingQuickFix(typed, ScalaInspectionBundle.message("replace.with.star")) { typed =>
              val innerText = typed.expr match {
                case _: ScInfixExpr | _: ScPostfixExpr | _: ScPrefixExpr => s"(${typed.expr.getText})"
                case expr if expr.getText.endsWith("_") => typed.expr.getText + " "
                case _ => typed.expr.getText
              }
              ScalaPsiElementFactory.createExpressionWithContextFromText(s"call($innerText*)", typed.getContext, typed)
                .asInstanceOf[ScMethodCall].argumentExpressions.head
            }
          )
        case named@ScNamingPattern(seqWildcard: ScSeqWildcardPattern) if convertNamedWildcardPattern &&
          features.`Scala 3 vararg splice syntax` && seqWildcard.isWildcard =>
          holder.registerProblem(
            named,
            getDisplayName,
            createReplacingQuickFix(named, ScalaInspectionBundle.message("replace.with.name.followed.by.star", named.name)) { named =>
              ScalaPsiElementFactory.createPatternFromTextWithContext(s"Seq(${named.name}*)", named.getContext, named)
                .asInstanceOf[ScConstructorPattern].args.patterns.head
            }
          )
        case withKw@ElementType(ScalaTokenTypes.kWITH) & Parent(compoundType: ScCompoundTypeElement) if convertCompoundTypes &&
          features.`& instead of with` && !compoundType.getParent.is[ScTypePattern] =>
          holder.registerProblem(
            withKw,
            getDisplayName,
            createReplacingQuickFix(withKw, ScalaInspectionBundle.message("replace.with.and.char")) { withKw =>
              ScalaPsiElementFactory.createIdentifier("&")(withKw).getPsi
            }
          )
        case _ =>
      }
    }
  }

  override def getOptionsPane: OptPane = pane(
    checkbox("convertWildcardUnderscore", ScalaInspectionBundle.message("suggest.converting.wildcards")),
    checkbox("addGeneratorCase", ScalaInspectionBundle.message("suggest.adding.case.in.for.comprehensions")),
    checkbox("convertWildcardImport", ScalaInspectionBundle.message("suggest.using.star.instead.of.underscore")),
    checkbox("convertImportAlias", ScalaInspectionBundle.message("suggest.using.as.instead.of.arrow")),
    checkbox("convertVarArgSplices", ScalaInspectionBundle.message("suggest.converting.vararg.splices")),
    checkbox("convertNamedWildcardPattern", ScalaInspectionBundle.message("suggest.converting.named.wildcard.patterns")),
    checkbox("convertCompoundTypes", ScalaInspectionBundle.message("suggest.using.and.instead.of.with")),
  )
}

object Source3Inspection {
  //private val `.getText`: Qualified = invocation("getText").from(ArraySeq(psiElementFqn, psiASTNodeFqn))
  //private val `.contains`: Qualified = invocation("contains")

  object ScWildcardTypeElementUnderscore {
    def unapply(wildcardType: ScWildcardTypeElement): Option[(ScWildcardTypeElement, PsiElement)] =
      wildcardType.findFirstChildByType(ScalaTokenTypes.tUNDER).map(wildcardType -> _)
  }

  private def isUpgradableImportWildcard(element: PsiElement): Boolean = {
    // example: import test.{x => _}
    def isNotShadowingAlias = element.prevSibling.forall(_.elementType == ScalaTokenTypes.tDOT)

    def isInRightElement = element.getParent match {
      case _: ScImportExpr => element.features.`Scala 3 wildcard imports`
      case _: ScImportSelector => element.features.`Scala 3 wildcard imports in selector`
      case _ => false
    }

    isInRightElement && isNotShadowingAlias
  }

  private def createReplacingQuickFix[T <: PsiElement](element: T, @Nls name: String)(transform: T => PsiElement): LocalQuickFix =
    new AbstractFixOnPsiElement(name: String, element) {
      override protected def doApplyFix(element: T)(implicit project: Project): Unit = {
        element.replace(transform(element))
      }
    }

  private def generatorType(gen: ScGenerator): Option[ScType] =
    for {
      desugared <- gen.desugared
      (_, param) <- desugared.analogMethodCall.matchedParameters.headOption
      ty <- Some(param.expectedType).collect { case ParameterizedType(_, Seq(p, _)) => p }
    } yield ty
}
