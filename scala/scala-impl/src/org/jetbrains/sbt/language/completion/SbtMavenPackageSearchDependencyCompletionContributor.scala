package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.{instanceOf, string}
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.model.completion.MavenVersionNegatingWeigher
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep
import org.jetbrains.sbt.language.utils.{PackageSearchApiHelper, SbtDependencyTraverser, SbtDependencyUtils}

import java.util.concurrent.ConcurrentLinkedDeque

class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement.inside(
        instanceOf(classOf[ScInfixExpr]) && (psiElement.withChild(psiElement.withText("libraryDependencies")) ||
          psiElement.withText(string.oneOf(VALID_OPS: _*))) ||
          SbtPsiElementPatterns.sbtModuleIdPattern)

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, results: CompletionResultSet): Unit = {

      val place = positionFromParameters(params)
      implicit val p: Project = place.getProject

      if (place.textMatches(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED))
        return

      results.restartCompletionOnAnyPrefixChange()

      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def waitAndAdd(
                      searchPromise: Promise[Integer],
                      cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo],
                      handler: MavenRepositoryArtifactInfo => Unit): Unit = {
        while (searchPromise.getState == Promise.State.PENDING || !cld.isEmpty()) {
          ProgressManager.checkCanceled()
          val item = cld.poll()
          if (item != null) {
            handler(item)
          }
        }
      }

      def completeGroup(resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
        resultSet.addElement(LookupElementBuilder.create(repo.getGroupId))
        resultSet.stopHere()
      }

      def completeArtifact(resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
        resultSet.addElement(LookupElementBuilder.create(repo.getArtifactId.replaceAll("_\\d+\\.\\d+.*$", "")))
        resultSet.stopHere()
      }

      def completeVersion(groupId: String, artifactId: String, resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
        if (repo.getGroupId == groupId && repo.getArtifactId == artifactId)
          repo.getItems.foreach(item => {
            resultSet.addElement(LookupElementBuilder.create(item.getVersion))
          })

        resultSet.stopHere()
      }

      def trimDummy(text: String) = text.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "").replaceAll("\"", "")
      val cleanText = trimDummy(place.getText)

      var extractedContents: List[Any] = List()
      def callback(psiElement: PsiElement):Boolean = {
        psiElement match {
          case stringLiteral: ScStringLiteral =>
            extractedContents = stringLiteral.getText :: extractedContents
            return false
          case _ =>
        }
        true
      }

      val scalaVer: String = place.scalaLanguageLevelOrDefault.getVersion

      place.parentOfType(classOf[ScInfixExpr], strict = false).foreach(expr => {
        if (VALID_OPS.contains(expr.operation.refName)) {
          expr.getText.split('%').map(_.trim).count(_.nonEmpty) - 1 match {
            case 1 if !(expr.right.isInstanceOf[ScReferenceExpression] &&
                expr.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION)) =>
              if (expr.left.textMatches(place.getText)) {
                val searchPromise = PackageSearchApiHelper.searchGroupArtifact(
                  cleanText,
                  "",
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld)

                waitAndAdd(searchPromise, cld, completeGroup(results))
                return
              }
              else if (expr.right.textMatches(place.getText)) {
                expr.left match {
                  case s: ScStringLiteral =>
                    extractedContents = s.getText :: extractedContents
                  case ref: ScReferenceExpression =>
                    inReadAction(SbtDependencyTraverser.traverseReferenceExpr(ref)(callback))
                }
                val searchPromise = PackageSearchApiHelper.searchGroupArtifact(
                  extractedContents(0).asInstanceOf[String],
                  cleanText,
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld)

                waitAndAdd(searchPromise, cld, completeArtifact(results))
                return
              }
            case x if x > 1 => inReadAction {
              val deps = SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(expr, mode = GetDep)
              if (deps.nonEmpty) {
                extractedContents = SbtDependencyUtils.processLibraryDependencyFromExprAndString(deps.head.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)])
                val dep = extractedContents.map(str => trimDummy(str.asInstanceOf[String]))
                if (dep.length >= 3) {
                  val groupId = extractedContents(0).asInstanceOf[String]
                  val artifactId = s"${extractedContents(1).asInstanceOf[String]}_$scalaVer"
                  val searchPromise = PackageSearchApiHelper.searchVersion(
                    groupId,
                    artifactId,
                    dependencySearch,
                    PackageSearchApiHelper.createSearchParameters(params),
                    cld)
                  val newResults = results.withRelevanceSorter(CompletionService.getCompletionService.emptySorter().weigh(
                    new MavenVersionNegatingWeigher()))

                  waitAndAdd(searchPromise, cld, completeVersion(groupId, artifactId, newResults))
                  return
                }
              }
            }
            case _ =>
          }
        }
        else if (expr.operation.refName == "+=" && expr.left.textMatches("libraryDependencies")) {
          val searchPromise = PackageSearchApiHelper.searchGroupArtifact(
            cleanText,
            "",
            dependencySearch,
            PackageSearchApiHelper.createSearchParameters(params),
            cld)

          waitAndAdd(searchPromise, cld, completeGroup(results))
          return
        }
      })

      if (place.getContext != null && place.getContext.getContext != null) {
        val superContext = place.getContext.getContext
        superContext match {
          case patDef: ScPatternDefinition if SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText) =>
            val lastLeaf = patDef.lastLeaf
            if (trimDummy(lastLeaf.getText) == cleanText) {
              val searchPromise = PackageSearchApiHelper.searchGroupArtifact(
                cleanText,
                "",
                dependencySearch,
                PackageSearchApiHelper.createSearchParameters(params),
                cld)

              waitAndAdd(searchPromise, cld, completeGroup(results))
              return
            }
          case _ =>
        }
      }
    }
  })


}