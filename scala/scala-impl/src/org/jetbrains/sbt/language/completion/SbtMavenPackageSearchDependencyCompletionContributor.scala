package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionService, CompletionType, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
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
import org.jetbrains.sbt.language.utils.PackageSearchApiHelper.waitAndAdd
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep
import org.jetbrains.sbt.language.utils.{CustomPackageSearchApiHelper, PackageSearchApiHelper, SbtArtifactInfo, SbtDependencyCommon, SbtDependencyTraverser, SbtDependencyUtils}

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}

class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement.inside(SbtPsiElementPatterns.sbtModuleIdPattern)

  private val RENDERING_PLACEHOLDER = "Sbtzzz"

  private val JAVA_VERSION_FLAG = "java"

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = try {
      val depCache = collection.mutable.Map[String, Boolean]()
      val place = positionFromParameters(params)
      implicit val project: Project = place.getProject

      val versions = new ConcurrentHashMap[String, List[String]]()
      val scalaVers = SbtDependencyUtils.getAllScalaVersionsOrDefault(place, majorOnly = true)

      resultSet.restartCompletionOnAnyPrefixChange()

      val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
      val dependencySearch = DependencySearchService.getInstance(place.getProject)

      def generateCompletedDependency(dep: String): List[String] = {
        var depList: List[String] = dep.split(':').toList
        if (depList.length == 2) depList = List.concat(depList, List(""))
        depList
      }

      def addArtifactResult(result: String, fillArtifact: Boolean, resultSet: CompletionResultSet): Unit = {
        val depList: List[String] = generateCompletedDependency(result)
        val artifactText = SbtDependencyUtils.generateArtifactText(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtDependencyCommon.defaultLibScope))
        val partialArtifactText = artifactText.split("%").filter(_.nonEmpty).map(_.trim).slice(1, artifactText.length).mkString(" % ")
        if (!(depCache contains artifactText)) {
          resultSet.addElement(new LookupElement {
            override def getLookupString: String = result + RENDERING_PLACEHOLDER

            override def renderElement(presentation: LookupElementPresentation): Unit = {
              if (fillArtifact)
                presentation.setItemText(partialArtifactText)
              else
                presentation.setItemText(artifactText)
              presentation.setItemTextBold(true)
            }

            override def handleInsert(context: InsertionContext): Unit = {
              val artifactExpr = SbtDependencyUtils.generateArtifactPsiExpression(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtDependencyCommon.defaultLibScope))
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
      }

      def completeDependency(fillArtifact: Boolean = false, resultSet: CompletionResultSet): MavenRepositoryArtifactInfo => Unit = repo => try {
        addArtifactResult(s"${repo.getGroupId}:${repo.getArtifactId}:", fillArtifact, resultSet)
        resultSet.stopHere()
      } catch {
        case e: Exception =>
          throw e
      }

      def addVersionResult(result: String, tailText: String, resultSet: CompletionResultSet):Unit = {
        resultSet.addElement(new LookupElement {

          override def getLookupString: String = result

          override def renderElement(presentation: LookupElementPresentation): Unit = {
            presentation.setItemText(result)
            presentation.setItemTextBold(true)
            presentation.setTailText(" ")
            presentation.appendTailText(tailText, true)
          }
        })
      }

      def fillInVersions(groupId: String, artifactId: String, scalaVer: String): Unit = {
        CustomPackageSearchApiHelper
          .waitAndAddDependencyVersions(
            groupId,
            artifactId,
            (lib: MavenRepositoryArtifactInfo) => {
              lib.getItems.foreach(item => versions.put(item.getVersion, (if (scalaVer != null) scalaVer else JAVA_VERSION_FLAG) :: versions.getOrDefault(item.getVersion, Nil)))

            }
          )
      }

      def completeVersions(resultSet: CompletionResultSet): Unit = {
        versions.forEach((version: String, supportedScalaVersions: List[String]) => {
          if (supportedScalaVersions.head == JAVA_VERSION_FLAG || supportedScalaVersions.length == scalaVers.length) {
            addVersionResult(version, "", resultSet)
          } else {
            addVersionResult(version, s"(scalaVer ${supportedScalaVersions.mkString(", ")})", resultSet)
          }
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

                waitAndAdd(searchPromise, cld, completeDependency(resultSet = resultSet))
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

                waitAndAdd(searchPromise, cld, completeDependency(fillArtifact = fillArtifact, resultSet = resultSet))
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
                  val artifactId = s"${extractedContents(1).asInstanceOf[String]}"
                  if (groupId == null || groupId.isEmpty || artifactId == null || artifactId.isEmpty) return

                  val newResultSet = resultSet.withRelevanceSorter(CompletionService.getCompletionService.defaultSorter(params, resultSet.getPrefixMatcher).weigh(SbtDependencyVersionWeigher))

                  val isScalaLib = SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)
                  if (!isScalaLib) {
                    fillInVersions(groupId, artifactId, null)
                  } else {
                    scalaVers.foreach(scalaVer => fillInVersions(groupId, SbtDependencyUtils.buildScalaDependencyString(artifactId, scalaVer), scalaVer))
                  }

                  completeVersions(newResultSet)
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

              waitAndAdd(searchPromise, cld, completeDependency(resultSet = resultSet))
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

                      waitAndAdd(searchPromise, cld, completeDependency(resultSet = resultSet))
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

              waitAndAdd(searchPromise, cld, completeDependency(resultSet = resultSet))
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

                    waitAndAdd(searchPromise, cld, completeDependency(resultSet = resultSet))
                    return
                  }
                })
              case _ =>
            }
          case _ =>
        }
      }
    } catch {
      case _ : Exception =>

    }
  })


}