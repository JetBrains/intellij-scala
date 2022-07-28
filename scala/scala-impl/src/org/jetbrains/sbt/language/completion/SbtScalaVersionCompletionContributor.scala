package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.sbt.language.completion.SbtScalaVersionCompletionContributor.{SbtScalaVersionCompletionProvider, SbtScalaVersionLookupElement}
import org.jetbrains.sbt.language.utils.{CustomPackageSearchApiHelper, CustomPackageSearchParams, SbtDependencyUtils, SbtExtendedArtifactInfo}

import java.util.concurrent.ConcurrentLinkedDeque
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private abstract class SbtScalaVersionCompletionContributor extends CompletionContributor {
  protected def pattern: ElementPattern[_ <: PsiElement]

  protected def provider: SbtScalaVersionCompletionProvider

  extend(CompletionType.BASIC, pattern, provider)
}

private object SbtScalaVersionCompletionContributor {
  class SbtScalaVersionLookupElement(version: String) extends LookupElement {
    override def getLookupString: String = version
  }

  abstract class SbtScalaVersionCompletionProvider extends CompletionProvider[CompletionParameters] {
    protected def getVersionsByLang(lang: Language): Seq[String]

    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit =
      try {
        val place = positionFromParameters(parameters)
        val versions = getVersionsByLang(place.getLanguage)
        val sorter = CompletionSorter
          .emptySorter()
          .weigh(new RealPrefixMatchingWeigher)
          .weigh(SbtDependencyVersionWeigher)
        val newResult = result
          .withRelevanceSorter(sorter)
          .withPrefixMatcher(trimDummy(place.getText))
        val res = versions
          .sortWith(SbtDependencyUtils.isGreaterStableVersion)
          .map(getVersionLookupElement)
        newResult.addAllElements(res.asJava)
        newResult.stopHere()
      } catch {
        case c: ControlFlowException => throw c
        case _: Exception =>
      }

    protected def getVersionLookupElement(version: String): SbtScalaVersionLookupElement

    protected def collectVersionsFromArtifact(groupId: String, artifactId: String, versionsBuffer: mutable.Growable[String]): Unit =
      try {
        val cld = new ConcurrentLinkedDeque[SbtExtendedArtifactInfo]()
        val searchFuture = CustomPackageSearchApiHelper
          .searchDependencyVersions(groupId, artifactId, CustomPackageSearchParams(useCache = true), cld)
        CustomPackageSearchApiHelper
          .waitAndAdd(
            searchFuture,
            cld,
            (lib: SbtExtendedArtifactInfo) => lib.versions.foreach { item =>
              if (isVersionStable(item)) {
                versionsBuffer += item
              }
            }
          )
      } catch {
        case c: ControlFlowException => throw c
        case _: Exception =>
      }

    private def isVersionStable(version: String): Boolean = {
      val unstablePattern = """.*[a-zA-Z-].*"""
      !version.matches(unstablePattern)
    }

    private def trimDummy(text: String) =
      text.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "").replaceAll("\"", "")
  }
}

private class ScalaVersionCompletionContributor extends SbtScalaVersionCompletionContributor {
  override protected def pattern: ElementPattern[_ <: PsiElement] =
    (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement().inside(SbtPsiElementPatterns.versionPattern)

  override protected def provider: SbtScalaVersionCompletionProvider = new SbtScalaVersionCompletionProvider {
    override protected def getVersionsByLang(lang: Language): Seq[String] =
      if (lang.getID == ScalaLanguage.INSTANCE.getID) {
        val versions = mutable.ListBuffer.empty[String]
        /* Scala 2 versions */
        collectVersionsFromArtifact("org.scala-lang", "scala-compiler", versions)
        /* Scala 3 versions */
        collectVersionsFromArtifact("org.scala-lang", "scala3-compiler_3", versions)
        versions.result()
      } else Seq.empty[String]

    override protected def getVersionLookupElement(version: String): SbtScalaVersionLookupElement =
      new SbtScalaVersionLookupElement(version)
  }
}
