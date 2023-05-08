package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.nio.file.{Path, Paths}
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils._

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
  def parse(strings: Seq[String]): Option[ExpressionEvaluationArguments] = strings match {
    case Seq(
      stringToPath(outDir),
      stringToPaths(classpath),
      stringToSequence(scalacOptions),
      stringToPath(source),
      s2i(line),
      expression,
      stringToSet(localVariableNames),
      packageName
    ) =>
      Some(ExpressionEvaluationArguments(outDir, classpath, scalacOptions, source, line, expression, localVariableNames, packageName))
    case _ => None
  }

  private val stringToPath: Extractor[String, Path] = Paths.get(_)

  private val stringToPaths: Extractor[String, Seq[Path]] = { paths =>
    if (paths.isEmpty) Seq.empty
    else paths.split(SerializationUtils.Delimiter).map(stringToPath).toSeq
  }

  private val stringToSequence: Extractor[String, Seq[String]] = SerializationUtils.stringToSequence(_)

  private val stringToSet: Extractor[String, Set[String]] = stringToSequence.andThen(_.toSet)(_)

  private val s2i: Extractor[String, Int] = _.toInt
}
