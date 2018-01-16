package org.jetbrains.plugins.scala.lang.parser.parsing.builder

/**
  * User: Dmitry.Naydanov
  * Date: 11.01.18.
  */
trait ProjectAwarePsiBuilder {
  def isTrailingCommasEnabled: Boolean
  def isIdBindingEnabled: Boolean
  def isMetaEnabled: Boolean
}
