package org.jetbrains.plugins.scala.server

import scala.util.Try

object CompileServerProperties {

  final val IsScalaCompileServer = "ij.scala.compile.server"
  
  def isMyselfScalaCompileServer: Boolean = {
    val optionResult = for {
      value <- sys.props.get(IsScalaCompileServer)
      booleanValue <- Try(value.toBoolean).toOption
    } yield booleanValue
    optionResult.getOrElse(false)
  }
}
