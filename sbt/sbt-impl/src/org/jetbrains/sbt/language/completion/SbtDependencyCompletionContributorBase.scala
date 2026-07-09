package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiClassExt, PsiElementExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.completion.*
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher.DependencyVersion
import org.jetbrains.plugins.scala.packagesearch.lang.completion.{BaseDependencyCompletionParameters, DependencyVersionWeigher}
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.sbt.language.completion.SbtDependencyCompletionProviderBase.*
import org.jetbrains.sbt.language.utils.*

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.chaining.scalaUtilChainingOps

abstract class SbtDependencyCompletionContributorBase extends CompletionContributor with DumbAware {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
    psiElement.inside(SbtPsiElementPatterns.sbtModuleIdPattern)

  protected def provider: SbtDependencyCompletionProviderBase

  extend(CompletionType.BASIC, PATTERN, provider)
}

final class SbtDependencyVersionCompletionContributor extends SbtDependencyCompletionContributorBase {
  override protected def provider: SbtDependencyCompletionProviderBase = new SbtDependencyVersionCompletionProvider
}

abstract class SbtDependencyCompletionProviderBase extends CompletionProvider[CompletionParameters] {
  final override protected def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
    val place = positionFromParameters(using parameters)
    resultSet.restartCompletionOnAnyPrefixChange()
    doAddCompletions(place)(using parameters, resultSet)
  }

  private def doAddCompletions(positionFromParams: PsiElement)(using params: CompletionParameters, resultSet: CompletionResultSet): Unit = {
    // replace the current element if there is any, otherwise insert text at the caret position
    def defaultRangeMarkerToReplace: RangeMarker = {
      val originalPositionParent = params.getOriginalPosition.nullSafe.map(_.getContext).get

      val range =
        if (originalPositionParent == null || trimDummyText(positionFromParams.getText).isEmpty) {
          TextRange.EMPTY_RANGE.shiftRight(params.getOffset)
        } else originalPositionParent.getTextRange

      params.getEditor.getDocument.createRangeMarker(range).tap { marker =>
        marker.setGreedyToLeft(true)
        marker.setGreedyToRight(true)
      }
    }

    positionFromParams.nullSafe.map(_.getContext)
      .filterByType[ScExpression]
      .filter(_.is[ScReferenceExpression, ScStringLiteral])
      .foreach { implicit parent =>
        parent.getContext match {
          case infix: ScInfixExpr =>
            infix match {
              // 1. libraryDependencies += ref<caret>
              case ScInfixExpr(ScReferenceExpression.refName(LibraryDependencies), ScReferenceExpression.refName("+="), `parent`) =>
                suggestGroupIdCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace))
              // 2. ref<caret> %% [...] // org
              case ScInfixExpr(`parent`, ScReferenceExpression.refName("%%" | "%"), _) =>
                val isOrgArtifact = hasSuitableType(infix, OrgArtifactFqn)
                if (isOrgArtifact) {
                  // replace the whole infix expression along with the artifactId but keep the version if it exists
                  val range = infix.getTextRange.grown(-CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED.length)
                  val marker = params.getEditor.getDocument.createRangeMarker(range)
                  marker.setGreedyToRight(true)
                  suggestGroupIdCompletions(new DependencyCompletionParameters(marker), withVersion = needsEmptyVersion(infix))
                }
              // 3. [...] %% ref<caret> // artifact
              case ScInfixExpr(lhs, ScReferenceExpression.refName("%%"), `parent`) =>
                getArtifactPart(lhs).foreach { groupId =>
                  suggestArtifactIdCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace), groupId, withVersion = needsEmptyVersion(infix))
                }
              // 4. [...] % ref<caret> // artifact or version
              case ScInfixExpr(lhs, ScReferenceExpression.refName("%"), `parent`) =>
                getArtifactPart(lhs) match {
                  case Some(groupId) =>
                    suggestArtifactIdCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace), groupId, withVersion = needsEmptyVersion(infix))
                  case None =>
                    lhs match {
                      case ref: ScReferenceExpression =>
                        // if DependencyBuilders.OrganizationArtifactName -> complete version, otherwise ignore
                        ref match {
                          case ReferenceResolvableToValOrDef((_, expr)) if hasSuitableType(expr, OrgArtifactFqn) =>
                            expr match {
                              case infix@ScInfixExpr(_, ScReferenceExpression.refName("%" | "%%"), _) =>
                                suggestVersionCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace), infix)
                              case _ => // ignore
                            }
                          case _ => // ignore
                        }
                      case infix@ScInfixExpr(_, ScReferenceExpression.refName("%" | "%%"), _) =>
                        suggestVersionCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace), infix)
                      case _ => // ignore
                    }
                }
              case _ => // ignore
            }
          case (_: ScArgumentExprList) &
            Parent((call: ScMethodCall) &
              Parent(ScInfixExpr(ScReferenceExpression.refName(LibraryDependencies), ScReferenceExpression.refName("++="), rhs)))
            if rhs == call && org.jetbrains.plugins.scala.codeInspection.collections.isSeq(call) =>
            // complete dependencies (e.g.: `libraryDependencies ++= Seq(ref<caret>)`)
            suggestGroupIdCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace))
          case _ if hasSuitableExpectedType(parent, ModuleIdFqn) =>
            // complete dependencies (e.g.: `val dep: ModuleID = ref<caret>`)
            // complete dependencies (e.g.: `val deps: Seq[ModuleID] = Seq(ref<caret>)`)
            suggestGroupIdCompletions(new DependencyCompletionParameters(defaultRangeMarkerToReplace))
          case _ => // ignore
        }
      }
  }

  protected final def extractText(expr: ScExpression, trimDummy: Boolean = false)
                                 (using params: CompletionParameters): Option[String] = {
    def doExtract(rawText: String, textOffset: Int): String = if (trimDummy) {
      val cleanText = trimDummyText(rawText)
      // extract only prefix before the caret
      // e.g.: `"""com.exa<caret>mple"""` -> `com.exa`
      //        ^  ^
      //        |  |_ textOffset
      //        |_ expr.startOffset
      cleanText.slice(0, params.getOffset - textOffset)
    } else rawText

    expr match {
      case ref: ScReferenceExpression =>
        Some(doExtract(ref.refName, ref.startOffset))
      case str: ScStringLiteral =>
        Some(doExtract(str.getValue, str.contentRange.getStartOffset))
      case _ => None
    }
  }

  protected final def getArtifactPart(expr: ScExpression)(using params: CompletionParameters): Option[String] = expr match {
    case str: ScStringLiteral => extractText(str)
    case ReferenceResolvableToValOrDef((_, str: ScStringLiteral)) => extractText(str)
    case _ => None
  }

  private def needsEmptyVersion(infix: ScInfixExpr): Boolean = infix.getContext match {
    case ScInfixExpr(`infix`, ScReferenceExpression.refName("%"), _) => false // probably already has a version
    case _ => true
  }

  /**
   * Suggest completions for dependencies where either group id or the whole dependency is expected.
   *
   * @param withVersion if true, add a version stub in the inserted string
   */
  protected def suggestGroupIdCompletions(params: DependencyCompletionParameters, withVersion: Boolean = true): Unit

  /**
   * Suggest completions for dependencies where artifact id is expected.
   *
   * @param withVersion if true, add a version stub in the inserted string
   */
  protected def suggestArtifactIdCompletions(params: DependencyCompletionParameters, groupId: String, withVersion: Boolean): Unit

  /**
   * Suggest completions for dependencies where a version is expected.
   *
   * @param infix an expression that contains the group id and artifact id separated by `%` or `%%`
   */
  protected def suggestVersionCompletions(params: DependencyCompletionParameters, infix: ScInfixExpr): Unit

  protected final def addAllAndStopIfInsideString(elements: Seq[LookupElement])
                                                 (using resultSet: CompletionResultSet, place: ScExpression): Unit = {
    resultSet.addAllElements(elements.asJava)
    stopIfInsideString(resultSet, place)
  }

  protected final def stopIfInsideString(resultSet: CompletionResultSet, place: ScExpression): Unit =
    if (place.is[ScStringLiteral]) resultSet.stopHere()
}

final class SbtDependencyVersionCompletionProvider extends SbtDependencyCompletionProviderBase {
  // Will be handled by coordinates completion provider
  override protected def suggestGroupIdCompletions(params: DependencyCompletionParameters, withVersion: Boolean): Unit = {}

  // Will be handled by coordinates completion provider
  override protected def suggestArtifactIdCompletions(params: DependencyCompletionParameters, groupId: String, withVersion: Boolean): Unit = {}

  override protected def suggestVersionCompletions(params: DependencyCompletionParameters, infix: ScInfixExpr): Unit = {
    getArtifactPart(infix.left)(using params.completionParams).foreach { groupId =>
      getArtifactPart(infix.right)(using params.completionParams).foreach { artifactId =>
        val artifactIdSuffix = infix.operation.refName match {
          case "%%" if SbtDependencyUtils.SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST.contains(s"$groupId:$artifactId") =>
            ArtifactIdSuffix.FullScalaVersion
          case "%%" => ArtifactIdSuffix.ScalaVersion
          case _ => ArtifactIdSuffix.Empty
        }

        val descriptor = DependencyDescriptor(groupId = groupId, artifactId = artifactId, version = None, artifactIdSuffix = artifactIdSuffix)
        val versions = DependencyUtil.getDependencyVersions(descriptor, context = infix.operation, onlyStable = !params.completionParams.isExtendedCompletion)
        val lookupElements = versions.map { version =>
          val presentableText = s"\"$version\""
          LookupElementBuilder.create(DependencyVersion(version), version.toString)
            .withIcon(AllIcons.Build.CompletionLocalCache)
            .withInsertHandler { (context, _) =>
              val marker = params.marker
              context.getDocument.replaceString(marker.getStartOffset, marker.getEndOffset, presentableText)
              // move the caret before the closing quote in the version string
              context.getEditor.getCaretModel.moveToOffset(marker.getStartOffset + presentableText.length - 1)
            }
        }

        val sorter = CompletionSorter.emptySorter()
          .weigh(new RealPrefixMatchingWeigher)
          .weigh(DependencyVersionWeigher)
        addAllAndStopIfInsideString(lookupElements)(using params.resultSet.withRelevanceSorter(sorter), params.place)
      }
    }
  }
}

object SbtDependencyCompletionProviderBase {
  final class DependencyCompletionParameters(
    val marker: RangeMarker,
    val withGroupId: Boolean = false,
    val withEmptyVersion: Boolean = false,
  )(
    using place: ScExpression,
  )(
    using completionParams: CompletionParameters, resultSet: CompletionResultSet,
  ) extends BaseDependencyCompletionParameters(completionParams, resultSet, place) {
    def copy(withGroupId: Boolean = false, withEmptyVersion: Boolean = false): DependencyCompletionParameters =
      new DependencyCompletionParameters(marker, withGroupId, withEmptyVersion)
  }

  private val LibraryDependencies = "libraryDependencies"
  private val ModuleIdFqn = "sbt.librarymanagement.ModuleID"
  private val OrgArtifactFqn = "sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName"

  private def trimDummyText(text: String) = text.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "")

  private object ReferenceResolvableToValOrDef {
    def unapply(expr: ScExpression): Option[(ScReferenceExpression, ScExpression)] = expr match {
      case ref@ScReferenceExpression((_: ScBindingPattern) & ScalaPsiUtil.inNameContext(ScPatternDefinition.expr(expr))) =>
        Some((ref, expr))
      case ref@ScReferenceExpression(ScFunctionDefinition.withBody(expr)) =>
        Some((ref, expr))
      case _ => None
    }
  }

  private def hasSuitableExpectedType(expr: ScExpression, fqns: String*): Boolean =
    isSameOrInheritor(expr, fqns *)(_.expectedType())

  private def hasSuitableType(e: Typeable & PsiElement, fqns: String*): Boolean =
    isSameOrInheritor(e, fqns *)(_.`type`().toOption)

  private def isSameOrInheritor[E <: PsiElement](element: E, fqns: String*)(getType: E => Option[ScType]): Boolean =
    getType(element).exists {
      case ExtractClass(cls) =>
        val elementScope = element.elementScope
        fqns.flatMap(elementScope.getCachedClass)
          .exists(cls.sameOrInheritor)
      case _ => false
    }
}
