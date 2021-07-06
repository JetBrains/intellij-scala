package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.sbt.language.utils.PackageSearchApiHelper

import java.util.concurrent.ConcurrentLinkedDeque

class SbtScalaVersionCompletionContributor extends CompletionContributor{
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
    psiElement.inside(SbtPsiElementPatterns.scalaVersionPattern)

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = try {

      var scalaVersionSeq: Seq[String] = Seq.empty
      val place = positionFromParameters(parameters)
      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def isVersionStable(version: String): Boolean = {
        val unstablePattern = """.*[a-zA-Z-].*"""
        !version.matches(unstablePattern)
      }

      def compareVersion(left: String, right: String): Boolean = {
        left.split("\\.")
          .zipAll(right.split("\\."), "0", "0")
          .find {case(a, b) => a != b }
          .fold(0) { case (a, b) => a.toInt - b.toInt } >= 0
      }

      def addVersion(groupId: String, artifactId: String, resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
        if (repo.getGroupId == groupId && repo.getArtifactId == artifactId) {
          repo.getItems.foreach(item => {
            if (isVersionStable(item.getVersion)) {
              scalaVersionSeq = scalaVersionSeq :+ item.getVersion
            }
          })
        }
      }

      def getVersionFromArtifact(groupId: String, artifactId: String): Unit = try {
        val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
          groupId,
          artifactId,
          dependencySearch,
          PackageSearchApiHelper.createSearchParameters(parameters),
          cld
        )

        PackageSearchApiHelper.waitAndAdd(searchPromise, cld, addVersion(groupId, artifactId, result))
      } catch {
        case e: Exception =>
      }

      /*
        Scala 2 versions
       */
      getVersionFromArtifact("org.scala-lang", "scala-compiler")

      /*
        Scala 3 versions
       */
      getVersionFromArtifact("org.scala-lang", "scala3-compiler_3")

      val newResult = result.withRelevanceSorter(
        CompletionService.getCompletionService.defaultSorter(parameters, result.getPrefixMatcher).weigh(SbtDependencyVersionWeigher)
      )
      scalaVersionSeq.foreach(ver => newResult.addElement(LookupElementBuilder.create(ver)))
      newResult.stopHere()
    } catch {
      case e: Exception =>
    }
  })
}
