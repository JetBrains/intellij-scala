package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.Arguments

sealed trait CompileServerArgs {

  def command: String

  def token: String

  def asStrings: Seq[String]

  final def asStringsWithoutToken: Seq[String] =
    asStrings.tail // token must be the first argument
}

object CompileServerArgs {

  case class Compile(arguments: Arguments)
    extends CompileServerArgs {

    override def command: String = Commands.Compile

    override def token: String = arguments.token

    override def asStrings: Seq[String] = arguments.asStrings
  }

  case class CompileJps(token: String,
                        projectPath: String,
                        globalOptionsPath: String)
    extends CompileServerArgs {

    override def command: String = Commands.CompileJps

    override def asStrings: Seq[String] = Seq(token, projectPath, globalOptionsPath)
  }
}

