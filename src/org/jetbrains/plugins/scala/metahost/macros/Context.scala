package org.jetbrains.plugins.scala.metahost.macros

import scala.meta._

class Context extends macros.Context {
  override def dialect: Dialect = ???
  override def warning(msg: String) = ???
  override def error(msg: String) = ???
  override def abort(msg: String) = ???
  override def resources = ???
}
