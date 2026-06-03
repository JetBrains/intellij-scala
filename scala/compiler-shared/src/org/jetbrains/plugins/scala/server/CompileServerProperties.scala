package org.jetbrains.plugins.scala.server

object CompileServerProperties {

  final val IsScalaCompileServer = "ij.scala.compile.server"

  final val SystemDirectoryProperty = "scala.compile.server.system.dir"

  final val LogDirectory = "scala.compile.server.log.dir"
  
  def isMyselfScalaCompileServer: Boolean = {
    val optionResult = for {
      value <- sys.props.get(IsScalaCompileServer)
      booleanValue <- value.toBooleanOption
    } yield booleanValue
    optionResult.getOrElse(false)
  }
}
