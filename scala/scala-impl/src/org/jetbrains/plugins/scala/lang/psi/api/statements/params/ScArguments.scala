package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

trait ScArguments extends ScalaPsiElement {
  def getArgsCount: Int
}