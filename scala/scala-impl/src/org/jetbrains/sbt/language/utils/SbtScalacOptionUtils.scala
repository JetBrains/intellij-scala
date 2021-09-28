package org.jetbrains.sbt.language.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.{Key, ModificationTracker}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.isSeq
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.io.Source
import scala.util.{Failure, Success, Using}

object SbtScalacOptionUtils {
  private val log = LoggerFactory.getLogger(getClass)

  val SCALAC_OPTIONS = "scalacOptions"

  val SCALAC_OPTIONS_DOC_KEY: Key[String] = Key.create("SCALAC_OPTION_DOC")

  def matchesScalacOptionsSbtSetting(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => ref.refName == SCALAC_OPTIONS
    // e.g.: ThisBuild / scalacOptions
    case ScInfixExpr(_, op, right: ScReferenceExpression) =>
      op.refName == "/" && right.refName == SCALAC_OPTIONS
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
    element.parents.collectFirst {
      case expr: ScInfixExpr if matchesScalacOptionsSbtSetting(expr.left) &&
        (if (isSeq(expr.right)) expr.operation.refName == "++=" else expr.operation.refName == "+=") =>
        expr
    }

  private def isScalacOptionInternal(element: PsiElement): Boolean =
    getScalacOptionsSbtSettingParent(element).isDefined

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  def scalacOptionsByFlag: Map[String, Seq[SbtScalacOptionInfo]] =
    getScalacOptions.groupBy(_.flag)

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def scalacOptionFlagsWithPrefix: Seq[(String, String)] = getScalacOptions.collect {
    case SbtScalacOptionInfo(flag, _, _, ArgType.OneAfterPrefix(prefix), _, _) =>
      prefix -> flag
  }

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  def getScalacOptions: Seq[SbtScalacOptionInfo] = {
    if (ApplicationManager.getApplication.isUnitTestMode) return scalacOptionsForUnitTests

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

  def getScalacOptionsForLiteralValue(str: ScStringLiteral): Seq[SbtScalacOptionInfo] =
    Option(str.getValue).filter(_.startsWith("-")).toSeq.flatMap { value =>
      def prefixed: Seq[SbtScalacOptionInfo] =
        scalacOptionFlagsWithPrefix
          .collect { case (prefix, flag) if value.startsWith(prefix) => flag }
          .flatMap(scalacOptionsByFlag.getOrElse(_, Seq.empty))

      scalacOptionsByFlag.getOrElse(value.split(":", 2).head, prefixed)
    }

  private def scalacOptionsForUnitTests: Seq[SbtScalacOptionInfo] = {
    import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
    import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType

    val versions = Set(Scala_2_11, Scala_2_12, Scala_2_13, Scala_3_0)

    Seq(
      SbtScalacOptionInfo(
        flag = "-deprecation",
        descriptions = Map(
          "Emit warning and location for usages of deprecated APIs." -> Set(Scala_2_11, Scala_3_0),
          "Emit warning and location for usages of deprecated APIs. See also -Wconf. [false]" -> Set(Scala_2_12, Scala_2_13)
        ),
        choices = Map.empty,
        argType = ArgType.No,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-classpath",
        descriptions = Map("Specify where to find user class files." -> versions),
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = Some("."),
      ),
      SbtScalacOptionInfo(
        flag = "-bootclasspath",
        descriptions = Map("Override location of bootstrap class files." -> versions),
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-Ydump-classes",
        descriptions = Map("Dump the generated bytecode to .class files (useful for reflective compilation that utilizes in-memory classloaders)." -> versions),
        choices = Map.empty,
        argType = ArgType.OneSeparate,
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-Yno-generic-signatures",
        descriptions = Map(
          "Suppress generation of generic signatures for Java." -> Set(Scala_2_11, Scala_3_0),
          "Suppress generation of generic signatures for Java. [false]" -> Set(Scala_2_12, Scala_2_13)
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
          "Print out program after <phases>" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "Print out program after" -> Set(Scala_3_0)
        ),
        scalaVersions = versions,
        defaultValue = None,
      ),
      SbtScalacOptionInfo(
        flag = "-language",
        argType = ArgType.Multiple,
        choices = Map(
          "experimental.macros" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "higherKinds" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "existentials" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "dynamics" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "reflectiveCalls" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "implicitConversions" -> Set(Scala_2_11, Scala_2_12, Scala_2_13),
          "postfixOps" -> Set(Scala_2_11, Scala_2_12, Scala_2_13)
        ),
        descriptions = Map(
          "Enable or disable language features: `_' for all, `-language:help' to list" -> Set(Scala_2_11),
          "Enable or disable language features: `_' for all, `-language:help' to list choices." -> Set(Scala_2_12),
          "Enable or disable language features" -> Set(Scala_2_13),
          "Enable one or more language features." -> Set(Scala_3_0)
        ),
        scalaVersions = versions,
        defaultValue = None
      )
    )
  }
}
