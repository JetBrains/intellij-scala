package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.properties.{PropertiesFileType, PropertiesLanguage}
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.sbt.language.utils.PackageSearchApiHelper

import java.util.concurrent.ConcurrentLinkedDeque

class SbtScalaVersionCompletionContributor extends CompletionContributor{
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern || SbtPsiElementPatterns.propertiesFilePattern) &&
    psiElement.inside(SbtPsiElementPatterns.versionPattern)

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = try {

      var versionSeq: Seq[String] = Seq.empty
      val place = positionFromParameters(parameters)
      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def isVersionStable(version: String): Boolean = {
        val unstablePattern = """.*[a-zA-Z-].*"""
        !version.matches(unstablePattern)
      }

      def addVersion(groupId: String, artifactId: String): MavenRepositoryArtifactInfo => Unit = repo => {
        if (repo.getGroupId == groupId && repo.getArtifactId == artifactId) {
          repo.getItems.foreach(item => {
            if (isVersionStable(item.getVersion)) {
              versionSeq = versionSeq :+ item.getVersion
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
          cld,
          false
        )

        PackageSearchApiHelper.waitAndAdd(searchPromise, cld, addVersion(groupId, artifactId))
      } catch {
        case e: Exception =>
      }

      val scalaLangInstanceID = ScalaLanguage.INSTANCE.getID
      val propertiesLangInstanceID = PropertiesLanguage.INSTANCE.getID

      place.getLanguage.getID match {
        case `scalaLangInstanceID` =>
          /*
           Scala 2 versions
          */
          getVersionFromArtifact("org.scala-lang", "scala-compiler")

          /*
            Scala 3 versions
          */
          getVersionFromArtifact("org.scala-lang", "scala3-compiler_3")

        case `propertiesLangInstanceID` =>
          getVersionFromArtifact("org.scala-sbt", "sbt-launch")

        case _ =>
      }



      val newResult = result.withRelevanceSorter(
        CompletionService.getCompletionService.defaultSorter(parameters, result.getPrefixMatcher).weigh(SbtDependencyVersionWeigher)
      )

      implicit val project: Project = place.getProject

      versionSeq.foreach(ver => newResult.addElement(new LookupElement {
        override def getLookupString: String = ver
        override def handleInsert(context: InsertionContext):Unit = {
          val caretModel = context.getEditor.getCaretModel
          val psiElement = context.getFile.findElementAt(caretModel.getVisualLineStart).getParent.getLastChild
          inWriteAction {
            psiElement match {
              case value: PropertyValueImpl =>
                val newPropertiesFile = PsiFileFactory.getInstance(project)
                  .createFileFromText(
                    s"dummy.${PropertiesFileType.INSTANCE.getDefaultExtension}",
                    PropertiesLanguage.INSTANCE,
                    s"sbt.version = $ver",
                    false, true
                  )
                  .asInstanceOf[PropertiesFile]
                value.replace(newPropertiesFile.getContainingFile.getFirstChild.getLastChild.getLastChild)
              case _ =>
            }
          }
        }
      }))
      newResult.stopHere()
    } catch {
      case e: Exception =>
    }
  })
}
