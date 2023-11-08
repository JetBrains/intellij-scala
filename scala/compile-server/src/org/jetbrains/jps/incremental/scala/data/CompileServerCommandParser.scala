package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.incremental.scala.remote.{CommandIds, CompileServerCommand, SourceScope}
import org.jetbrains.plugins.scala.compiler.data.{ComputeStampsArguments, ExpressionEvaluationArguments}

import scala.util.Try

trait CompileServerCommandParser {

  def parse(command: String, args: Seq[String]): Try[CompileServerCommand]
}

object CompileServerCommandParser
  extends CompileServerCommandParser {

  def parse(commandId: String, args: Seq[String]): Try[CompileServerCommand] = Try {
    commandId match {
      case CommandIds.Compile =>
        ArgumentsParser.parse(args) match {
          case Right(arguments) => CompileServerCommand.Compile(arguments)
          case Left(t) => throw t
        }
      case CommandIds.ComputeStamps =>
        ComputeStampsArguments.parse(args) match {
          case Some(arguments) =>
            CompileServerCommand.ComputeStamps(arguments)
          case None =>
            throwIllegalArgs(commandId, args)
        }
      case CommandIds.CompileJps =>
        args match {
          case Seq(projectPath, globalOptionsPath, dataStorageRootPath, moduleName, sourceScope, other@_*) =>
            CompileServerCommand.CompileJps(
              projectPath = projectPath,
              globalOptionsPath = globalOptionsPath,
              dataStorageRootPath = dataStorageRootPath,
              moduleName = moduleName,
              sourceScope = SourceScope.fromString(sourceScope),
              externalProjectConfig = other.headOption
            )
          case _ =>
            throwIllegalArgs(commandId, args)
        }
      case CommandIds.EvaluateExpression =>
        val parsed = ExpressionEvaluationArguments.parse(args).get
        CompileServerCommand.EvaluateExpression(parsed)
      case CommandIds.GetMetrics =>
        args match {
          case Seq() =>
            CompileServerCommand.GetMetrics
          case _ =>
            throwIllegalArgs(commandId, args)
        }
      case unknownCommand =>
        throw new IllegalArgumentException(s"Unknown commandId: $unknownCommand")
    }
  }

  private def throwIllegalArgs(commandId: String, args: Seq[String]): Nothing =
    throw new IllegalArgumentException(s"Can't parse args for $commandId: ${args.toList}")
}

