package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.jps.incremental.scala.remote.{NioPathTranslator, PathTranslator}

import java.nio.file.Path

case class ExpressionEvaluationArguments(
  useBuiltInExpressionCompiler: Boolean,
  outDir: Path,
  classpath: Seq[Path],
  scalacOptions: Seq[String],
  source: Path,
  line: Int,
  expression: String,
  localVariableNames: Set[String],
  packageName: String
) {

  @deprecated(message = "Use asStrings(PathTranslator). Kept for preserving binary compatibility.", since = "2026.1")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def asStrings: Seq[String] = asStrings(NioPathTranslator)

  def asStrings(translator: PathTranslator): Seq[String] = {
    import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils.sequenceToString
    val pathToString: Path => String = translator.translate
    val pathsToString: Seq[Path] => String = paths => sequenceToString(paths.map(pathToString))

    Seq(
      useBuiltInExpressionCompiler.toString,
      pathToString(outDir),
      pathsToString(classpath),
      sequenceToString(scalacOptions),
      pathToString(source),
      line.toString,
      expression,
      sequenceToString(localVariableNames),
      packageName
    )
  }
}

object ExpressionEvaluationArguments {
  import Extractors.{StringToPath, StringToPaths, StringToSequence}

  def parse(strings: Seq[String]): Option[ExpressionEvaluationArguments] = strings match {
    case Seq(
      s2b(useBuiltInExpressionCompiler),
      StringToPath(outDir),
      StringToPaths(classpath),
      StringToSequence(scalacOptions),
      StringToPath(source),
      s2i(line),
      expression,
      stringToSet(localVariableNames),
      packageName
    ) =>
      Some(ExpressionEvaluationArguments(useBuiltInExpressionCompiler, outDir, classpath, scalacOptions, source, line, expression, localVariableNames, packageName))
    case _ => None
  }

  private val s2b: Extractor[String, Boolean] = _.toBoolean

  private val stringToSet: Extractor[String, Set[String]] = StringToSequence.andThen(_.toSet)(_)

  private val s2i: Extractor[String, Int] = _.toInt
}
