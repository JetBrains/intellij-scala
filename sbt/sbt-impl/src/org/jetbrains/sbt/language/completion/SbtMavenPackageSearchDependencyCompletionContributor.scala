package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, NonNullObjectExt, ObjectExt, PsiElementExt, inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClient
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep
import org.jetbrains.sbt.language.utils._

import scala.jdk.CollectionConverters.ListHasAsScala

// TODO(SCL-19130, SCL-22206): refactor
class SbtMavenPackageSearchDependencyCompletionContributor extends CompletionContributor {

  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
      psiElement.inside(SbtPsiElementPatterns.sbtModuleIdPattern)

  private val RENDERING_PLACEHOLDER = "Sbtzzz"
  private val JAVA_VERSION_FLAG = "java"

  extend(CompletionType.BASIC, PATTERN, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = try {
      resultSet.restartCompletionOnAnyPrefixChange()

      val place = positionFromParameters(params)
      implicit val project: Project = place.getProject

      val useCache: Boolean = !params.isExtendedCompletion || ApplicationManager.getApplication.isUnitTestMode
      val stableVersionOnly: Boolean = !params.isExtendedCompletion

      val depLookUp = collection.mutable.Map[String, Boolean]()
      var versions = Map[ComparableVersion, List[String]]()

      def generateCompletedDependency(dep: String): List[String] = {
        var depList: List[String] = dep.split(':').toList
        if (depList.length == 2) depList = List.concat(depList, List(""))
        depList
      }

      def addArtifactResult(result: String, fillArtifact: Boolean, resultSet: CompletionResultSet): Unit = {
        val depList: List[String] = generateCompletedDependency(result)
        val artifactText = SbtDependencyUtils.generateArtifactText(SbtArtifactInfo(depList(0), depList(1), depList(2), SbtDependencyCommon.defaultLibScope))
        val partialArtifactText = artifactText.split("%").filter(_.nonEmpty).map(_.trim).slice(1, artifactText.length).mkString(" % ")
        if (!(depLookUp contains artifactText)) {
          resultSet.addElement(LookupElementBuilder.create(result + RENDERING_PLACEHOLDER)
            .withRenderer { (_, presentation) =>
              val itemText = if (fillArtifact) partialArtifactText else artifactText
              presentation.setItemText(itemText)
              presentation.setItemTextBold(true)
            }
            .withInsertHandler { (context, item) =>
              val artifactExpr = SbtDependencyUtils.generateArtifactPsiExpression(
                SbtArtifactInfo(depList(0), depList(1), depList(2), SbtDependencyCommon.defaultLibScope),
                context.getFile
              )
              val caretModel = context.getEditor.getCaretModel

              val psiElement = context.getFile.findElementAt(context.getStartOffset)

              var parentElemToChange = psiElement.getContext

              parentElemToChange match {
                case ref: ScReferenceExpression =>
                  // inserted outside of the string literal
                  // replace inserted lookupString with placeholder and proceed

                  val startOffset = ref.startOffset
                  inWriteCommandAction {
                    context.getDocument.replaceString(startOffset, startOffset + item.getLookupString.length, RENDERING_PLACEHOLDER)
                  }
                  context.commitDocument()
                  parentElemToChange = context.getFile.findElementAt(startOffset)
                  parentElemToChange = parentElemToChange.getParent
                case _ =>
              }

              while (parentElemToChange.getParent.is[ScInfixExpr] &&
                MODULE_ID_OPS.contains(parentElemToChange.getParent.asInstanceOf[ScInfixExpr].operation.refName)
              ) {
                parentElemToChange = parentElemToChange.getParent
              }
              parentElemToChange = parentElemToChange.getParent

              inWriteCommandAction {
                parentElemToChange match {
                  case exprList: ScArgumentExprList =>
                    exprList.exprs.foreach { expr =>
                      if (expr.getText.contains(RENDERING_PLACEHOLDER)) {
                        val offset = expr.getTextOffset
                        expr.replace(artifactExpr)
                        caretModel.moveToOffset(offset + artifactExpr.getTextLength - 1)
                      }
                    }
                  case patDef: ScPatternDefinition =>
                    patDef.expr.get.replace(artifactExpr)
                    caretModel.moveToOffset(patDef.getTextOffset + patDef.getTextLength - 1)
                  case infix: ScInfixExpr =>
                    infix.right.replace(artifactExpr)
                    caretModel.moveToOffset(infix.getTextOffset + infix.getTextLength - 1)
                  case _ =>
                }
              }
              context.commitDocument()
            })
          depLookUp += (artifactText -> true)
        }
      }

      def completeDependencies(groupIdText: String, artifactIdText: String, resultSet: CompletionResultSet, fillArtifact: Boolean): Unit = {
        val groupId = formatString(groupIdText)
        val artifactId = formatString(artifactIdText)
        val packagesFuture = PackageSearchClient.instance().searchByQuery(groupId, artifactId, useCache)
        val packages = ProgressIndicatorUtils.awaitWithCheckCanceled(packagesFuture)
          .asScala.toList
          .filterByType[ApiMavenPackage]
          .pipeIf(fillArtifact)(_.filter(_.getGroupId == groupId))

        packages.foreach { pkg =>
          addArtifactResult(s"${pkg.getGroupId}:${pkg.getArtifactId}:", fillArtifact, resultSet)
        }
        resultSet.stopHere()
      }

      def generateVersionLookupElement(version: ComparableVersion, tailText: String, weigh: Int = 0): LookupElement = {
        PrioritizedLookupElement.withPriority(new LookupElement {
          override def getLookupString: String = version.toString

          override def renderElement(presentation: LookupElementPresentation): Unit = {
            presentation.setItemText(version.toString)
            if (SbtDependencyUtils.isVersionStable(version.toString)) presentation.setItemTextBold(true)
            presentation.setTailText(" ")
            presentation.appendTailTextItalic(tailText, true)
          }
        }, weigh)
      }

      def addVersionResult(version: ComparableVersion, tailText: String, resultSet: CompletionResultSet): Unit = {
        resultSet.addElement(generateVersionLookupElement(version, tailText))
      }

      def fillInVersions(groupId: String, artifactId: String, scalaVer: String): Unit = {
        val artifactVersions = DependencyUtil.getArtifactVersions(groupId, artifactId, stableVersionOnly)
        artifactVersions.foreach { version =>
          versions = versions + (version -> ((if (scalaVer != null) scalaVer else JAVA_VERSION_FLAG) :: versions.getOrElse(version, Nil)))
        }
      }

      def completeVersions(resultSet: CompletionResultSet): Unit = {
        versions.foreach { case (version, scalaVersions) =>
          if (scalaVersions.head == JAVA_VERSION_FLAG) {
            addVersionResult(version, "", resultSet)
          } else {
            addVersionResult(version, s"(${scalaVersions.mkString(", ")})", resultSet)
          }
        }
        resultSet.stopHere()
      }

      def trimDummy(text: String) = text.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "").replaceAll("\"", "")
      val cleanText = trimDummy(place.getText)

      var extractedContents: List[Any] = List()
      def callback(psiElement: PsiElement): Boolean = {
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
            case 1 if !(expr.right.is[ScReferenceExpression] &&
              expr.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION)) =>
              if (expr.left.textMatches(place.getText)) {
                completeDependencies(cleanText, "", resultSet, fillArtifact = false)
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

                completeDependencies(groupId, cleanText, resultSet, fillArtifact)
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

                  val newResultSet = resultSet.withRelevanceSorter(CompletionSorter.emptySorter().weigh(new RealPrefixMatchingWeigher).weigh(SbtDependencyVersionWeigher))

                  val isScalaLib = SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)
                  if (!isScalaLib) {
                    fillInVersions(groupId, artifactId, null)
                  } else {
                    val scalaVers = if (SbtDependencyUtils.SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST contains s"$groupId:$artifactId")
                      SbtDependencyUtils.getAllScalaVersionsOrDefault(place) else
                      SbtDependencyUtils.getAllScalaVersionsOrDefault(place, majorOnly = true)
                    scalaVers.foreach(scalaVer => fillInVersions(groupId, SbtDependencyUtils.buildScalaArtifactIdString(groupId, artifactId, scalaVer), scalaVer))
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
              completeDependencies(cleanText, "", resultSet, fillArtifact = false)
              return

            /*
              e.g. libraryDependencies ++= Seq("")
             */
            case "++=" =>
              expr.right match {
                case seq: ScMethodCall if seq.deepestInvokedExpr.textMatches(SbtDependencyUtils.SEQ) =>
                  seq.args.exprs.foreach { expr =>
                    if (trimDummy(expr.getText) == cleanText) {
                      completeDependencies(cleanText, "", resultSet, fillArtifact = false)
                      return
                    }
                  }
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
              completeDependencies(cleanText, "", resultSet, fillArtifact = false)
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
                    completeDependencies(cleanText, "", resultSet, fillArtifact = false)
                    return
                  }
                })
              case _ =>
            }
          case _ =>
        }
      }
    } catch {
      case _: Exception =>
    }
  })

  private def formatString(str: String) = str.replaceAll("^\"+|\"+$", "")
}
