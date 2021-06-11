package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.{instanceOf, string}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.util.ProcessingContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.model.completion.MavenVersionNegatingWeigher
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep
import org.jetbrains.sbt.language.utils.{PackageSearchApiHelper, SbtArtifactInfo, SbtCommon, SbtDependencyTraverser, SbtDependencyUtils}
import org.jetbrains.sbt.project.data.ModuleExtData

import java.util.concurrent.ConcurrentLinkedDeque

class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement.inside(
        instanceOf(classOf[ScInfixExpr]) && (psiElement.withChild(psiElement.withText("libraryDependencies")) ||
          psiElement.withText(string.oneOf(VALID_OPS: _*))) ||
          SbtPsiElementPatterns.sbtModuleIdPattern)

  private val RENDERING_PLACEHOLDER = "Sbtzzz"

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, results: CompletionResultSet): Unit = try {
      val depCache = collection.mutable.Map[String, Boolean]()
      val place = positionFromParameters(params)
      var containingScalaFile: ScalaFile = null
      place.getContainingFile match {
        case dummyHolder: JavaDummyHolder =>
          Option(dummyHolder.getContext).map(_.getContainingFile).foreach {
            case scalaFile: ScalaFile =>
              containingScalaFile = scalaFile
            case _ =>
          }
      }

      // Search for Scala Version
      implicit val project: Project = place.getProject
      val module: Module = ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(
        Option(containingScalaFile.getVirtualFile).getOrElse(containingScalaFile.getViewProvider.getVirtualFile))
      var scalaVer: String = ""
      val moduleExtData = SbtUtil.getModuleData(project, ExternalSystemApiUtil.getExternalProjectId(module), ModuleExtData.Key).toSeq
      if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

      if (scalaVer == "") {
        scalaVer = place.scalaLanguageLevelOrDefault.getVersion
      }

      results.restartCompletionOnAnyPrefixChange()

      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def generateCompletedDependency(dep: String): List[String] = {
        var depList: List[String] = dep.split(':').toList
        if (depList.length == 2) depList = List.concat(depList, List(""))
        depList
      }

      def addResult(results: CompletionResultSet, result: String): Unit = try {
        val depList: List[String] = generateCompletedDependency(result)
        val artifactText = SbtDependencyUtils.generateArtifactText(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtCommon.defaultLibScope))
        if (!(depCache contains artifactText)) {
          results.addElement(new LookupElement {
            override def getLookupString: String = result + RENDERING_PLACEHOLDER

            override def renderElement(presentation: LookupElementPresentation): Unit = {
              presentation.setItemText(artifactText)
              presentation.setItemTextBold(true)
            }

            override def handleInsert(context: InsertionContext): Unit = {
              val artifactExpr = SbtDependencyUtils.generateArtifactPsiExpression(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtCommon.defaultLibScope))
              val caretModel = context.getEditor.getCaretModel

              val psiElement = context.getFile.findElementAt(caretModel.getOffset)

              var parentElemToChange = psiElement.getContext
              while (parentElemToChange.getParent.isInstanceOf[ScInfixExpr] &&
                VALID_OPS.contains(parentElemToChange.getParent.asInstanceOf[ScInfixExpr].operation.refName)
              )
                parentElemToChange = parentElemToChange.getParent
              parentElemToChange = parentElemToChange.getParent

              inWriteCommandAction {
                parentElemToChange match {
                  case exprList: ScArgumentExprList =>
                    exprList.exprs.foreach(expr => {
                      if (expr.getText.contains(RENDERING_PLACEHOLDER)) {
                        val offset = expr.getTextOffset
                        expr.replace(artifactExpr)
                        context.getEditor.getCaretModel.moveToOffset(offset + artifactExpr.getTextLength - 1)
                      }
                    })
                  case patDef: ScPatternDefinition =>
                    patDef.expr.get.replace(artifactExpr)
                    context.getEditor.getCaretModel.moveToOffset(patDef.getTextOffset + patDef.getTextLength - 1)
                  case infix: ScInfixExpr =>
                    infix.right.replace(artifactExpr)
                    context.getEditor.getCaretModel.moveToOffset(infix.getTextOffset + infix.getTextLength - 1)
                }
              }
              context.commitDocument()
            }
          })
          depCache += (artifactText -> true)
        }
      } catch {
        case e: Exception =>
          throw e
      }

      def waitAndAdd(
                    searchPromise: Promise[Integer],
                    cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo],
                    handler: MavenRepositoryArtifactInfo => Unit): Unit = {
        while (searchPromise.getState == Promise.State.PENDING || !cld.isEmpty) {
          ProgressManager.checkCanceled()
          val item = cld.poll()
          if (item != null) {
            handler(item)
          }
        }
      }

      def completeDependency(resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => try {
        addResult(resultSet, s"${repo.getGroupId}:${repo.getArtifactId}:")
        resultSet.stopHere()
      } catch {
        case e: Exception =>
          throw e
      }

//      def completeGroup(resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
//        resultSet.addElement(LookupElementBuilder.create(repo.getGroupId))
//        resultSet.stopHere()
//      }
//
//      def completeArtifact(resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
//        resultSet.addElement(LookupElementBuilder.create(repo.getArtifactId.replaceAll("_\\d+\\.\\d+.*$", "")))
//        resultSet.stopHere()
//      }

      def completeVersion(groupId: String, artifactId: String, resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => {
        if (repo.getGroupId == groupId && (repo.getArtifactId == artifactId || repo.getArtifactId == s"${artifactId}_$scalaVer"))
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

      place.parentOfType(classOf[ScInfixExpr], strict = false).foreach(expr => {
        if (VALID_OPS.contains(expr.operation.refName)) {
          expr.getText.split('%').map(_.trim).count(_.nonEmpty) - 1 match {
            case 1 if !(expr.right.isInstanceOf[ScReferenceExpression] &&
                expr.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION)) =>
              if (expr.left.textMatches(place.getText)) {
                val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                  cleanText,
                  "",
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld)

                waitAndAdd(searchPromise, cld, completeDependency(results))
                return
              }
              else if (expr.right.textMatches(place.getText)) {
                expr.left match {
                  case s: ScStringLiteral =>
                    extractedContents = s.getText :: extractedContents
                  case ref: ScReferenceExpression =>
                    inReadAction(SbtDependencyTraverser.traverseReferenceExpr(ref)(callback))
                }
                val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                  extractedContents(0).asInstanceOf[String],
                  cleanText,
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld)

                waitAndAdd(searchPromise, cld, completeDependency(results))
                return
              }
            case x if x > 1 => inReadAction {
              val deps = SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(expr, mode = GetDep)
              if (deps.nonEmpty) {
                extractedContents = SbtDependencyUtils.processLibraryDependencyFromExprAndString(deps.head.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)])
                val dep = extractedContents.map(str => trimDummy(str.asInstanceOf[String]))
                if (dep.length >= 3) {
                  val groupId = extractedContents(0).asInstanceOf[String]
                  val artifactId = s"${extractedContents(1).asInstanceOf[String]}"
                  val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                    groupId,
                    artifactId,
                    dependencySearch,
                    PackageSearchApiHelper.createSearchParameters(params),
                    cld)
                  val newResults = results.withRelevanceSorter(CompletionService.getCompletionService.defaultSorter(params, results.getPrefixMatcher).weigh(SbtDependencyVersionWeigher))

                  waitAndAdd(searchPromise, cld, completeVersion(groupId, extractedContents(1).asInstanceOf[String], newResults))
                  return
                }
              }
            }
            case _ =>
          }
        }
        else if (expr.operation.refName == "+=" && expr.left.textMatches("libraryDependencies")) {
          val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
            cleanText,
            "",
            dependencySearch,
            PackageSearchApiHelper.createSearchParameters(params),
            cld)

          waitAndAdd(searchPromise, cld, completeDependency(results))
          return
        }
      })

      if (place.getContext != null && place.getContext.getContext != null) {
        val superContext = place.getContext.getContext
        superContext match {
          case patDef: ScPatternDefinition if SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText) =>
            val lastLeaf = patDef.lastLeaf
            if (trimDummy(lastLeaf.getText) == cleanText) {
              val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                cleanText,
                "",
                dependencySearch,
                PackageSearchApiHelper.createSearchParameters(params),
                cld)

              waitAndAdd(searchPromise, cld, completeDependency(results))
              return
            }
          case _ =>
        }
      }
    } catch {
      case e: Exception =>
        throw e
    }
  })


}