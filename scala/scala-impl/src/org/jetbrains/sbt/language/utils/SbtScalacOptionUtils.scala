package org.jetbrains.sbt.language.utils

import com.intellij.openapi.util.{Key, ModificationTracker}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.io.Source
import scala.util.{Failure, Success, Using}

object SbtScalacOptionUtils {
  private val log = LoggerFactory.getLogger(getClass)

  val SCALAC_OPTIONS = "scalacOptions"

  val SCALAC_OPTIONS_DOC_KEY: Key[String] = Key.create("SCALAC_OPTION_DOC")

  def matchesScalacOptions(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => ref.refName == SCALAC_OPTIONS
    // e.g.: ThisBuild / scalacOptions
    case ScInfixExpr(_, op, right: ScReferenceExpression) =>
      op.refName == "/" && right.refName == SCALAC_OPTIONS
    case _ => false
  }

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  def getScalacOptions: Seq[SbtScalacOptionInfo] = {
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
}
