package org.jetbrains.sbt.language.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.cached
import org.jetbrains.plugins.scala.codeInspection.collections.isSeq
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.annotation.tailrec
import scala.io.Source
import scala.util.{Failure, Success, Using}

object SbtScalacOptionUtils {
  private val log = LoggerFactory.getLogger(getClass)

  val SCALAC_OPTIONS = "scalacOptions"

  val SEQ_OPS = Set("++=", "--=", ":=")
  val SINGLE_OPS = Set("+=", "-=")

  private def projectScalaVersions(project: Project): List[ScalaLanguageLevel] =
    SbtDependencyUtils.getAllScalaVers(project).flatMap(ScalaLanguageLevel.findByVersion)

  def projectVersionsSorted(project: Project, reverse: Boolean): List[ScalaLanguageLevel] = {
    val ordering = implicitly[Ordering[ScalaLanguageLevel]]

    projectScalaVersions(project).distinct.sorted(if (reverse) ordering.reverse else ordering)
  }

  @tailrec
  def matchesScalacOptionsSbtSetting(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => ref.refName == SCALAC_OPTIONS
    // e.g.: ThisBuild / scalacOptions
    case ScInfixExpr(_, op, right: ScReferenceExpression) =>
      op.refName == "/" && right.refName == SCALAC_OPTIONS
    case ScParenthesisedExpr(e) => matchesScalacOptionsSbtSetting(e)
    case _ => false
  }

  def withScalacOption[T](element: PsiElement)(onMismatch: => T, onMatch: ScStringLiteral => T): T =
    element.getParent match {
      case str: ScStringLiteral if isScalacOption(str) =>
        onMatch(str)
      case _ => onMismatch
    }

  def isScalacOption(str: ScStringLiteral): Boolean = isScalacOptionInternal(str)

  def isScalacOption(ref: ScReferenceExpression): Boolean = isScalacOptionInternal(ref)

  def getScalacOptionsSbtSettingParent(element: PsiElement): Option[ScInfixExpr] =
    element.contexts.collectFirst {
      case expr: ScInfixExpr if matchesScalacOptionsSbtSetting(expr.left) &&
        (if (isSeq(expr.right)) SEQ_OPS(expr.operation.refName) else SINGLE_OPS(expr.operation.refName)) =>
        expr
    }

  private def isScalacOptionInternal(element: PsiElement): Boolean =
    getScalacOptionsSbtSettingParent(element).isDefined

  def scalacOptionsByFlag: Map[String, Seq[SbtScalacOptionInfo]] = _scalacOptionsByFlag()

  private val _scalacOptionsByFlag = cached("SbtScalacOptionUtils.scalacOptionsByFlag", ModificationTracker.NEVER_CHANGED, () => {
    getScalacOptions.groupBy(_.flag)
  })

  private val scalacOptionFlagsWithPrefix = cached("SbtScalacOptionUtils.scalacOptionsFlagsWithPrefix", ModificationTracker.NEVER_CHANGED, () => {
    getScalacOptions.collect {
      case SbtScalacOptionInfo(flag, _, _, ArgType.OneAfterPrefix(prefix), _, _) =>
        prefix -> flag
    }
  })

  def getScalacOptions: Seq[SbtScalacOptionInfo] = _getScalacOptions()

  private val _getScalacOptions = cached("SbtScalacOptionUtils.getScalacOptions", ModificationTracker.NEVER_CHANGED, () => {
    if (ApplicationManager.getApplication.isUnitTestMode) scalacOptionsForUnitTests else {
      def scalacOptionsSource = {
        val completionContributorClass = SbtScalacOptionsCompletionContributor.getClass
        val inputStream = completionContributorClass.getResourceAsStream("scalac-options.json")
        Source.fromInputStream(inputStream)
      }

      val options = Using(scalacOptionsSource) { src =>
        src
          .mkString
          .parseJson
          .convertTo[Seq[SbtScalacOptionInfo]]
      }

      options match {
        case Success(value) => value
        case Failure(exception) =>
          log.error("Could not load scalac options", exception)
          Seq.empty
      }
    }
  })

  def getScalacOptionsForLiteralValue(str: ScStringLiteral): Seq[SbtScalacOptionInfo] =
    Option(str.getValue).filter(_.startsWith("-")).toSeq.flatMap { value =>
      def prefixed: Seq[SbtScalacOptionInfo] =
        scalacOptionFlagsWithPrefix()
          .collect { case (prefix, flag) if value.startsWith(prefix) => flag }
          .flatMap(scalacOptionsByFlag.getOrElse(_, Seq.empty))

      scalacOptionsByFlag.getOrElse(value.split(":", 2).head, prefixed)
    }

  private def scalacOptionsForUnitTests: Seq[SbtScalacOptionInfo] = {
    import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
    import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType

    val versions = Set(Scala_2_11, Scala_2_12, Scala_2_13, Scala_3_0, Scala_3_1)

    Seq(
      SbtScalacOptionInfo(
        flag = "-deprecation",
        descriptions = Map(
          Scala_2_11 -> "Emit warning and location for usages of deprecated APIs.",
          Scala_2_12 -> "Emit warning and location for usages of deprecated APIs. See also -Wconf. [false]",
          Scala_2_13 -> "Emit warning and location for usages of deprecated APIs. See also -Wconf. [false]",
          Scala_3_0 -> "Emit warning and location for usages of deprecated APIs.",
          Scala_3_1 -> "Emit warning and location for usages of deprecated APIs.",
        ),
        choices = Map.empty,
        argType = ArgType.No,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-classpath",
        descriptions = versions.map(_ -> "Specify where to find user class files.").toMap,
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = Some("."),
      ),
      SbtScalacOptionInfo(
        flag = "-bootclasspath",
        descriptions = versions.map(_ -> "Override location of bootstrap class files.").toMap,
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-Ydump-classes",
        descriptions = versions.map(_ -> "Dump the generated bytecode to .class files (useful for reflective compilation that utilizes in-memory classloaders).").toMap,
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-Yno-generic-signatures",
        descriptions = Map(
          Scala_2_11 -> "Suppress generation of generic signatures for Java.",
          Scala_2_12 -> "Suppress generation of generic signatures for Java. [false]",
          Scala_2_13 -> "Suppress generation of generic signatures for Java. [false]",
          Scala_3_0 -> "Suppress generation of generic signatures for Java.",
          Scala_3_1 -> "Suppress generation of generic signatures for Java.",
        ),
        choices = Map.empty,
        argType = ArgType.No,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-Xprint",
        argType = ArgType.Multiple,
        choices = Map.empty,
        descriptions = Map(
          Scala_2_11 -> "Print out program after <phases>",
          Scala_2_12 -> "Print out program after <phases>",
          Scala_2_13 -> "Print out program after <phases>",
          Scala_3_0 -> "Print out program after",
          Scala_3_1 -> "Print out program after",
        ),
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-language",
        argType = ArgType.Multiple,
        choices = Map(
          Scala_2_11 -> Set(
            "experimental.macros",
            "higherKinds",
            "existentials",
            "dynamics",
            "reflectiveCalls",
            "implicitConversions",
            "postfixOps"
          ),
          Scala_2_12 -> Set(
            "experimental.macros",
            "higherKinds",
            "existentials",
            "dynamics",
            "reflectiveCalls",
            "implicitConversions",
            "postfixOps"
          ),
          Scala_2_13 -> Set(
            "experimental.macros",
            "higherKinds",
            "existentials",
            "dynamics",
            "reflectiveCalls",
            "implicitConversions",
            "postfixOps"
          )
        ),
        descriptions = Map(
          Scala_2_11 -> "Enable or disable language features: `_' for all, `-language:help' to list",
          Scala_2_12 -> "Enable or disable language features: `_' for all, `-language:help' to list choices.",
          Scala_2_13 -> "Enable or disable language features",
          Scala_3_0 -> "Enable one or more language features.",
          Scala_3_1 -> "Enable one or more language features.",
        ),
        scalaVersions = versions,
        defaultValue = None
      )
    )
  }
}
