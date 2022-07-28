package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration

trait ScExistentialClause extends ScalaPsiElement {
  def declarations : Seq[ScDeclaration] = findChildren[ScDeclaration]
}