package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.{Arguments, ComputeStampsArguments, ExpressionEvaluationArguments}

sealed trait CompileServerCommand {
  def asArgs: Seq[String]

  def id: String

  def isCompileCommand: Boolean
}

object CompileServerCommand {

  case class Compile(arguments: Arguments)
    extends CompileServerCommand {

    override def id: String = CommandIds.Compile

    override def asArgs: Seq[String] = arguments.asStrings

    override def isCompileCommand: Boolean = true
  }

  case class ComputeStamps(arguments: ComputeStampsArguments) extends CompileServerCommand {
    override def asArgs: Seq[String] = arguments.asStrings

    override def id: String = CommandIds.ComputeStamps

    override def isCompileCommand: Boolean = true
  }

  /**
   * @param externalProjectConfig Some(path) in case build system supports storing project configuration outside `.idea` folder
   */
  case class CompileJps(projectPath: String,
                        globalOptionsPath: String,
                        dataStorageRootPath: String,
                        moduleName: String,
                        sourceScope: SourceScope,
                        externalProjectConfig: Option[String])
    extends CompileServerCommand {

    override def id: String = CommandIds.CompileJps

    override def asArgs: Seq[String] = Seq(
      projectPath,
      globalOptionsPath,
      dataStorageRootPath,
      moduleName,
      sourceScope.toString,
    ) ++ externalProjectConfig

    override def isCompileCommand: Boolean = true
  }

  case class EvaluateExpression(args: ExpressionEvaluationArguments) extends CompileServerCommand {
    override def asArgs: Seq[String] = args.asStrings

    override def id: String = CommandIds.EvaluateExpression

    override def isCompileCommand: Boolean = true
  }

  case object GetMetrics extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq.empty

    override def id: String = CommandIds.GetMetrics

    override def isCompileCommand: Boolean = false
  }
}
