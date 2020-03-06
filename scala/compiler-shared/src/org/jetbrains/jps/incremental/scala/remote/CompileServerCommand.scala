package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.Arguments

sealed trait CompileServerCommand {

  def token: String

  def asArgs: Seq[String]

  def isCompilation: Boolean

  def id: String

  final def asArgsWithoutToken: Seq[String] =
    asArgs.tail // token must be the first argument
}

object CompileServerCommand {

  case class Compile(arguments: Arguments)
    extends CompileServerCommand {

    override def token: String = arguments.token

    override def isCompilation: Boolean = true

    override def id: String = CommandIds.Compile

    override def asArgs: Seq[String] = arguments.asStrings
  }

  case class CompileJps(token: String,
                        projectPath: String,
                        globalOptionsPath: String,
                        dataStorageRootPath: String)
    extends CompileServerCommand {

    override def isCompilation: Boolean = true

    override def id: String = CommandIds.CompileJps

    override def asArgs: Seq[String] = Seq(token, projectPath, globalOptionsPath, dataStorageRootPath)
  }

  case class GetState(token: String)
    extends CompileServerCommand {

    override def isCompilation: Boolean = false

    override def id: String = CommandIds.GetState

    override def asArgs: Seq[String] = Seq(token)
  }
}

