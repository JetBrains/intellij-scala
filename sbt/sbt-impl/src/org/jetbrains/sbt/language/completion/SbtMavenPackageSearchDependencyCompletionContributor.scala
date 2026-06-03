package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.LatestScalaVersions
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
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher.DependencyVersion
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.sbt.language.completion.SbtMavenPackageSearchDependencyCompletionProvider.*
import org.jetbrains.sbt.language.utils.*

import scala.annotation.unused
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.chaining.scalaUtilChainingOps

final class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor with DumbAware {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
    psiElement.inside(SbtPsiElementPatterns.sbtModuleIdPattern)

  extend(CompletionType.BASIC, PATTERN, new SbtMavenPackageSearchDependencyCompletionProvider)
}

final class SbtMavenPackageSearchDependencyCompletionProvider extends CompletionProvider[CompletionParameters] {
  override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
    val place = positionFromParameters(using parameters)
    resultSet.restartCompletionOnAnyPrefixChange()
    doAddCompletions(place)(using parameters, resultSet)
  }

  private def doAddCompletions(positionFromParams: PsiElement)(implicit params: CompletionParameters, resultSet: CompletionResultSet): Unit = {
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
                completeGroupId(defaultRangeMarkerToReplace)
              // 2. ref<caret> %% [...] // org
              case ScInfixExpr(`parent`, ScReferenceExpression.refName("%%" | "%"), _) =>
                val isOrgArtifact = hasSuitableType(infix, OrgArtifactFqn)
                if (isOrgArtifact) {
                  // replace the whole infix expression along with the artifactId but keep the version if it exists
                  val range = infix.getTextRange.grown(-CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED.length)
                  val marker = params.getEditor.getDocument.createRangeMarker(range)
                  marker.setGreedyToRight(true)
                  completeGroupId(marker, withVersion = needsEmptyVersion(infix))
                }
              // 3. [...] %% ref<caret> // artifact
              case ScInfixExpr(lhs, ScReferenceExpression.refName("%%"), `parent`) =>
                getArtifactPart(lhs).foreach { groupId =>
                  completeArtifactId(groupId, infix, defaultRangeMarkerToReplace)
                }
              // 4. [...] % ref<caret> // artifact or version
              case ScInfixExpr(lhs, ScReferenceExpression.refName("%"), `parent`) =>
                getArtifactPart(lhs) match {
                  case Some(groupId) =>
                    completeArtifactId(groupId, infix, defaultRangeMarkerToReplace)
                  case None =>
                    lhs match {
                      case ref: ScReferenceExpression =>
                        // if DependencyBuilders.OrganizationArtifactName -> complete version, otherwise ignore
                        ref match {
                          case ReferenceResolvableToValOrDef((_, expr)) if hasSuitableType(expr, OrgArtifactFqn) =>
                            expr match {
                              case infix@ScInfixExpr(_, ScReferenceExpression.refName("%" | "%%"), _) =>
                                completeVersion(infix, defaultRangeMarkerToReplace)
                              case _ => // ignore
                            }
                          case _ => // ignore
                        }
                      case infix@ScInfixExpr(_, ScReferenceExpression.refName("%" | "%%"), _) =>
                        completeVersion(infix, defaultRangeMarkerToReplace)
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
            completeGroupId(defaultRangeMarkerToReplace)
          case _ if hasSuitableExpectedType(parent, ModuleIdFqn) =>
            // complete dependencies (e.g.: `val dep: ModuleID = ref<caret>`)
            // complete dependencies (e.g.: `val deps: Seq[ModuleID] = Seq(ref<caret>)`)
            completeGroupId(defaultRangeMarkerToReplace)
          case _ => // ignore
        }
      }
  }

  private def extractText(expr: ScExpression, trimDummy: Boolean = false)
                         (implicit params: CompletionParameters): Option[String] = {
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

  private def getArtifactPart(expr: ScExpression)(implicit params: CompletionParameters): Option[String] = expr match {
    case str: ScStringLiteral => extractText(str)
    case ReferenceResolvableToValOrDef((_, str: ScStringLiteral)) => extractText(str)
    case _ => None
  }

  private def getArtifacts(@unused groupId: String, @unused artifactId: String, @unused exactMatchGroupId: Boolean)
                          (implicit @unused params: CompletionParameters): Seq[AnyRef /* ApiMavenPackage */] = {
//    val useCache = !params.isExtendedCompletion || ApplicationManager.getApplication.isUnitTestMode
//    val packages = DependencyUtil.getArtifacts(groupId, artifactId, useCache, exactMatchGroupId)
//    packages
    Seq.empty
  }

  private def needsEmptyVersion(infix: ScInfixExpr): Boolean = infix.getContext match {
    case ScInfixExpr(`infix`, ScReferenceExpression.refName("%"), _) => false // probably already has a version
    case _ => true
  }

  private def completeGroupId(marker: RangeMarker, withVersion: Boolean = true)
                             (implicit params: CompletionParameters, resultSet: CompletionResultSet, place: ScExpression): Unit =
    extractText(place, trimDummy = true).foreach { groupIdQuery =>
      // TODO: SCL-23246 Reimplement using new maven search api.
      val packages = getArtifacts(groupIdQuery, artifactId = "", exactMatchGroupId = false)
      val lookupElements = packages.flatMap(toLookupElement(_, marker, withGroupId = true, addEmptyVersion = withVersion))
//        .distinctBy(_.getLookupString)
      addAllAndStopIfInsideString(lookupElements)
    }

  private def completeArtifactId(groupId: String, infix: ScInfixExpr, marker: RangeMarker)
                                (implicit params: CompletionParameters, resultSet: CompletionResultSet, place: ScExpression): Unit =
    extractText(place, trimDummy = true).foreach { artifactIdQuery =>
      // TODO: SCL-23246 Reimplement using new maven search api.
      val withVersion = needsEmptyVersion(infix)
      val lookupElements = getArtifacts(groupId, artifactIdQuery, exactMatchGroupId = true)
        .flatMap(toLookupElement(_, marker, withGroupId = false, addEmptyVersion = withVersion))
//        .distinctBy(_.getLookupString)
      addAllAndStopIfInsideString(lookupElements)
    }

  private def completeVersion(infix: ScInfixExpr, marker: RangeMarker)
                             (implicit params: CompletionParameters, resultSet: CompletionResultSet, place: ScExpression): Unit =
    getArtifactPart(infix.left).foreach { groupId =>
      getArtifactPart(infix.right).foreach { artifactId =>
        val artifactIdSuffix = infix.operation.refName match {
          case "%%" if SbtDependencyUtils.SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST.contains(s"$groupId:$artifactId") =>
            ArtifactIdSuffix.FullScalaVersion
          case "%%" => ArtifactIdSuffix.ScalaVersion
          case _ => ArtifactIdSuffix.Empty
        }

        val descriptor = DependencyDescriptor(groupId = groupId, artifactId = artifactId, version = None, artifactIdSuffix = artifactIdSuffix)
        val versions = DependencyUtil.getDependencyVersions(descriptor, context = infix.operation, onlyStable = !params.isExtendedCompletion)
        val lookupElements = versions.map { version =>
          val presentableText = s"\"$version\""
          LookupElementBuilder.create(DependencyVersion(version), version.toString)
            .withInsertHandler { (context, _) =>
              context.getDocument.replaceString(marker.getStartOffset, marker.getEndOffset, presentableText)
              // move the caret before the closing quote in the version string
              context.getEditor.getCaretModel.moveToOffset(marker.getStartOffset + presentableText.length - 1)
            }
        }

        val sorter = CompletionSorter.emptySorter()
          .weigh(new RealPrefixMatchingWeigher)
          .weigh(DependencyVersionWeigher)
        addAllAndStopIfInsideString(lookupElements)(using resultSet.withRelevanceSorter(sorter), place)
      }
    }
}

object SbtMavenPackageSearchDependencyCompletionProvider {
  private val LibraryDependencies = "libraryDependencies"
  private val ModuleIdFqn = "sbt.librarymanagement.ModuleID"
  private val OrgArtifactFqn = "sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName"

  private def trimDummyText(text: String) = text.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "")

  private def addAllAndStopIfInsideString(elements: Seq[LookupElement])
                                         (implicit resultSet: CompletionResultSet, place: ScExpression): Unit = {
    resultSet.addAllElements(elements.asJava)
    if (place.is[ScStringLiteral]) {
      resultSet.stopHere()
    }
  }

  private val CrossPublishedArtifact = "^(.+)_(\\d+.*)$".r
  private val Scala2MajorVersions = LatestScalaVersions.allScala2.map(_.major)
  private val ScalaMajorVersions = Scala2MajorVersions :+ "3"

  // TODO: SCL-23246 Reimplement using new maven search api.
  private def toLookupElement(pkg: AnyRef /* ApiMavenPackage */, marker: RangeMarker, withGroupId: Boolean, addEmptyVersion: Boolean): None.type /* LookupElement */ = {
//    val groupId = pkg.getGroupId
//    val (delimiterLen, artifactId) = pkg.getArtifactId match {
//      case CrossPublishedArtifact(artifactId, version) if ScalaMajorVersions.contains(version) =>
//        (2, artifactId)
//      case id => (1, id)
//    }
//    val lookupString = s"$groupId${":" * delimiterLen}$artifactId"
//    val presentableText = {
//      val groupIdPrefix = if (withGroupId) s"\"$groupId\" ${"%" * delimiterLen} " else ""
//      val presentableArtifactText = s"\"$artifactId\""
//      val versionSuffix = if (addEmptyVersion) " % \"\"" else ""
//      groupIdPrefix + presentableArtifactText + versionSuffix
//    }
//
//    LookupElementBuilder.create(lookupString)
//      .withRenderer { (_, presentation) =>
//        presentation.setItemText(presentableText)
//        presentation.setItemTextBold(true)
//      }
//      .withInsertHandler { (context, _) =>
//        context.getDocument.replaceString(marker.getStartOffset, marker.getEndOffset, presentableText)
//        // move the caret before the closing quote in the artifactId/version string
//        context.getEditor.getCaretModel.moveToOffset(marker.getStartOffset + presentableText.length - 1)
//        if (addEmptyVersion) {
//          context.scheduleAutoPopup()
//        }
//      }
    None
  }

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
    isSameOrInheritor(expr, fqns*)(_.expectedType())

  private def hasSuitableType(e: Typeable & PsiElement, fqns: String*): Boolean =
    isSameOrInheritor(e, fqns*)(_.`type`().toOption)

  private def isSameOrInheritor[E <: PsiElement](element: E, fqns: String*)(getType: E => Option[ScType]): Boolean =
    getType(element).exists {
      case ExtractClass(cls) =>
        val elementScope = element.elementScope
        fqns.flatMap(elementScope.getCachedClass)
          .exists(cls.sameOrInheritor)
      case _ => false
    }
}
