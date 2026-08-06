package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadata
import org.jetbrains.plugins.scala.compiler.data.{Arguments, ComputeStampsArguments, DocumentCompilationArguments, ExpressionEvaluationArguments}

import java.nio.file.Path
import scala.annotation.unused

sealed trait CompileServerCommand {
  def asArgs(translator: PathTranslator): Seq[String]

  def id: String

  def isCompileCommand: Boolean
}

object CompileServerCommand {

  case class Compile(arguments: Arguments)
    extends CompileServerCommand {

    override def id: String = CommandIds.Compile

    override def asArgs(translator: PathTranslator): Seq[String] = arguments.asStrings(translator)

    override def isCompileCommand: Boolean = true
  }

  case class ComputeStamps(arguments: ComputeStampsArguments) extends CompileServerCommand {
    override def asArgs(translator: PathTranslator): Seq[String] = arguments.asStrings(translator)

    override def id: String = CommandIds.ComputeStamps

    override def isCompileCommand: Boolean = true
  }

  /**
   * @param externalProjectConfig Some(path) in case build system supports storing project configuration outside `.idea` folder
   */
  case class CompileJps(
    projectPath: Path,
    globalOptionsPath: Path,
    dataStorageRootPath: Path,
    moduleNames: Seq[String],
    sourceScope: SourceScope,
    projectMetadata: ScalaJpsProjectMetadata,
    externalProjectConfig: Option[Path]
  ) extends CompileServerCommand {

    override def id: String = CommandIds.CompileJps

    override def asArgs(translator: PathTranslator): Seq[String] = {
      import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils.{pathToString, sequenceToString}

      Seq(
        pathToString(projectPath, translator),
        pathToString(globalOptionsPath, translator),
        pathToString(dataStorageRootPath, translator),
        sequenceToString(moduleNames),
        sourceScope.toString,
        projectMetadata.asCompactJsonString
      ) ++ externalProjectConfig.map(pathToString(_, translator))
    }

    override def isCompileCommand: Boolean = true
  }

  case class CompileDocument(arguments: DocumentCompilationArguments) extends CompileServerCommand {
    override def asArgs(translator: PathTranslator): Seq[String] = DocumentCompilationArguments.serialize(arguments, translator)

    override def id: String = CommandIds.CompileDocument

    override def isCompileCommand: Boolean = true
  }

  case class EvaluateExpression(args: ExpressionEvaluationArguments) extends CompileServerCommand {
    override def asArgs(translator: PathTranslator): Seq[String] = args.asStrings(translator)

    override def id: String = CommandIds.EvaluateExpression

    override def isCompileCommand: Boolean = true
  }

  case object GetMetrics extends CompileServerCommand {

    override def asArgs(@unused translator: PathTranslator): Seq[String] = Seq.empty

    override def id: String = CommandIds.GetMetrics

    override def isCompileCommand: Boolean = false
  }
}
