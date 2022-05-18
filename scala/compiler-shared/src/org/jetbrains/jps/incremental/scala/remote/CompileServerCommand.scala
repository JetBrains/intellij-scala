package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.Arguments

import scala.concurrent.duration.FiniteDuration

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

  /**
   * @param externalProjectConfig Some(path) in case build system supports storing project configuration outside `.idea` folder
   */
  case class CompileJps(projectPath: String,
                        globalOptionsPath: String,
                        dataStorageRootPath: String,
                        externalProjectConfig: Option[String],
                        moduleNames: Seq[String])
    extends CompileServerCommand {

    import CompileJps.ExternalProjectConfigTag

    override def id: String = CommandIds.CompileJps

    override def asArgs: Seq[String] = Seq(
      projectPath,
      globalOptionsPath,
      dataStorageRootPath
    ) ++ externalProjectConfig.map(epc => s"$ExternalProjectConfigTag$epc") ++ moduleNames

    override def isCompileCommand: Boolean = true
  }

  object CompileJps {
    final val ExternalProjectConfigTag: String = "externalProjectConfig: "
  }

  case class GetMetrics()
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq.empty

    override def id: String = CommandIds.GetMetrics

    override def isCompileCommand: Boolean = false
  }

  // TODO replace with GetMetrics
  case class StartMetering(meteringInterval: FiniteDuration)
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq(meteringInterval.toSeconds.toString)

    override def id: String = CommandIds.StartMetering

    override def isCompileCommand: Boolean = false
  }

  // TODO replace with GetMetrics
  case class EndMetering()
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq()

    override def id: String = CommandIds.EndMetering

    override def isCompileCommand: Boolean = false
  }
}

