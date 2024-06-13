package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils._

import java.nio.file.Path

case class ExpressionEvaluationArguments(
  outDir: Path,
  classpath: Seq[Path],
  scalacOptions: Seq[String],
  source: Path,
  line: Int,
  expression: String,
  localVariableNames: Set[String],
  packageName: String
) {
  def asStrings: Seq[String] =
    Seq(
      pathToPathString(outDir),
      pathsToPathStrings(classpath),
      sequenceToString(scalacOptions),
      pathToPathString(source),
      line.toString,
      expression,
      sequenceToString(localVariableNames),
      packageName
    )
}

object ExpressionEvaluationArguments {
  import Extractors.{StringToPath, StringToPaths, StringToSequence}

  def parse(strings: Seq[String]): Option[ExpressionEvaluationArguments] = strings match {
    case Seq(
      StringToPath(outDir),
      StringToPaths(classpath),
      StringToSequence(scalacOptions),
      StringToPath(source),
      s2i(line),
      expression,
      stringToSet(localVariableNames),
      packageName
    ) =>
      Some(ExpressionEvaluationArguments(outDir, classpath, scalacOptions, source, line, expression, localVariableNames, packageName))
    case _ => None
  }

  private val stringToSet: Extractor[String, Set[String]] = StringToSequence.andThen(_.toSet)(_)

  private val s2i: Extractor[String, Int] = _.toInt
}
