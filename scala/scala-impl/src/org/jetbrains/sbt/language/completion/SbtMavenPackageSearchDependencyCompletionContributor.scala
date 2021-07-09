package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.sbt.language.utils.PackageSearchApiHelper.waitAndAdd
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep
import org.jetbrains.sbt.language.utils.{PackageSearchApiHelper, SbtArtifactInfo, SbtCommon, SbtDependencyTraverser, SbtDependencyUtils}

import java.util.concurrent.ConcurrentLinkedDeque

class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement.inside(SbtPsiElementPatterns.sbtModuleIdPattern)

  private val RENDERING_PLACEHOLDER = "Sbtzzz"

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, results: CompletionResultSet): Unit = try {
      val depCache = collection.mutable.Map[String, Boolean]()
      val place = positionFromParameters(params)
      implicit val project: Project = place.getProject

      val scalaVer = place.scalaLanguageLevelOrDefault.getVersion

      results.restartCompletionOnAnyPrefixChange()

      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def generateCompletedDependency(dep: String): List[String] = {
        var depList: List[String] = dep.split(':').toList
        if (depList.length == 2) depList = List.concat(depList, List(""))
        depList
      }

      def addResult(results: CompletionResultSet, result: String, fillArtifact: Boolean): Unit = try {
        val depList: List[String] = generateCompletedDependency(result)
        val artifactText = SbtDependencyUtils.generateArtifactText(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtCommon.defaultLibScope))
        val partialArtifactText = artifactText.split("%").filter(_.nonEmpty).map(_.trim).slice(1, artifactText.length).mkString(" % ")
        if (!(depCache contains artifactText)) {
          results.addElement(new LookupElement {
            override def getLookupString: String = result + RENDERING_PLACEHOLDER

            override def renderElement(presentation: LookupElementPresentation): Unit = {
              if (fillArtifact)
                presentation.setItemText(partialArtifactText)
              else
                presentation.setItemText(artifactText)
              presentation.setItemTextBold(true)
            }

            override def handleInsert(context: InsertionContext): Unit = {
              val artifactExpr = SbtDependencyUtils.generateArtifactPsiExpression(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtCommon.defaultLibScope))
              val caretModel = context.getEditor.getCaretModel

              val psiElement = context.getFile.findElementAt(caretModel.getOffset)

              var parentElemToChange = psiElement.getContext
              while (parentElemToChange.getParent.isInstanceOf[ScInfixExpr] &&
                MODULE_ID_OPS.contains(parentElemToChange.getParent.asInstanceOf[ScInfixExpr].operation.refName)
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

      def completeDependency(resultSet: CompletionResultSet, fillArtifact: Boolean = false): MavenRepositoryArtifactInfo => Unit = repo => try {
        addResult(resultSet, s"${repo.getGroupId}:${repo.getArtifactId}:", fillArtifact)
        resultSet.stopHere()
      } catch {
        case e: Exception =>
          throw e
      }

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
        if (MODULE_ID_OPS.contains(expr.operation.refName)) {
          expr.getText.split('%').map(_.trim).count(_.nonEmpty) - 1 match {
            case 1 if !(expr.right.isInstanceOf[ScReferenceExpression] &&
                expr.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION)) =>
              if (expr.left.textMatches(place.getText)) {
                val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                  cleanText,
                  "",
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld,
                  false
                )

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
                val groupId = extractedContents(0).asInstanceOf[String]
                val fillArtifact = trimDummy(groupId).nonEmpty
                val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                  groupId,
                  cleanText,
                  dependencySearch,
                  PackageSearchApiHelper.createSearchParameters(params),
                  cld,
                  fillArtifact
                )

                waitAndAdd(searchPromise, cld, completeDependency(results, fillArtifact))
                return
              }
            case x if x > 1 => inReadAction {
              val deps = SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(expr, mode = GetDep)
              if (deps.nonEmpty) {
                val libDepExprTuple = deps.head.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)]
                extractedContents = SbtDependencyUtils.processLibraryDependencyFromExprAndString(libDepExprTuple)
                val dep = extractedContents.map(str => trimDummy(str.asInstanceOf[String]))
                if (dep.length >= 3) {
                  val groupId = extractedContents(0).asInstanceOf[String]
                  var artifactId = s"${extractedContents(1).asInstanceOf[String]}"
                  if (SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)) artifactId = SbtDependencyUtils.buildScalaDependencyString(artifactId, scalaVer)
                  val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                    groupId,
                    artifactId,
                    dependencySearch,
                    PackageSearchApiHelper.createSearchParameters(params),
                    cld,
                    false
                  )
                  val newResults = results.withRelevanceSorter(CompletionService.getCompletionService.defaultSorter(params, results.getPrefixMatcher).weigh(SbtDependencyVersionWeigher))

                  waitAndAdd(searchPromise, cld, completeVersion(groupId, extractedContents(1).asInstanceOf[String], newResults))
                  return
                }
              }
            }
            case _ =>
          }
        }
        else if (expr.left.textMatches("libraryDependencies")) {
          expr.operation.refName match {
            case "+=" =>
              val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                cleanText,
                "",
                dependencySearch,
                PackageSearchApiHelper.createSearchParameters(params),
                cld,
                false
              )

              waitAndAdd(searchPromise, cld, completeDependency(results))
              return

            /*
              e.g. libraryDependencies ++= Seq("")
             */
            case "++=" =>
              expr.right match {
                case seq: ScMethodCall if seq.deepestInvokedExpr.textMatches(SbtDependencyUtils.SEQ) =>
                  seq.args.exprs.foreach(expr => {
                    if (trimDummy(expr.getText) == cleanText) {
                      val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                        cleanText,
                        "",
                        dependencySearch,
                        PackageSearchApiHelper.createSearchParameters(params),
                        cld,
                        false
                      )

                      waitAndAdd(searchPromise, cld, completeDependency(results))
                      return
                    }
                  })
              }
            case _ =>
          }

        }
      })

      if (place.getContext != null && place.getContext.getContext != null) {
        val superContext = place.getContext.getContext
        superContext match {
          /*
            e.g. val scalariformTest: ModuleID = ""
           */
          case patDef: ScPatternDefinition if SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText) =>
            val lastLeaf = patDef.lastLeaf
            if (trimDummy(lastLeaf.getText) == cleanText) {
              val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                cleanText,
                "",
                dependencySearch,
                PackageSearchApiHelper.createSearchParameters(params),
                cld,
                false
              )

              waitAndAdd(searchPromise, cld, completeDependency(results))
              return
            }
          /*
            e.g. val seqModuleIdList: Seq[ModuleID] = Seq(...)
           */
          case argList: ScArgumentExprList =>
            argList.getContext.getContext match {
              case patDef: ScPatternDefinition if SBT_MODULE_ID_TYPE.exists(patDef.`type`().getOrAny.canonicalText.contains) =>
                argList.exprs.foreach(expr => {
                  if (trimDummy(expr.getText) == cleanText) {
                    val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
                      cleanText,
                      "",
                      dependencySearch,
                      PackageSearchApiHelper.createSearchParameters(params),
                      cld,
                      false
                    )

                    waitAndAdd(searchPromise, cld, completeDependency(results))
                    return
                  }
                })
              case _ =>
            }
          case _ =>
        }
      }
    } catch {
      case e: Exception =>

    }
  })


}