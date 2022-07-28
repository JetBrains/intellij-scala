package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

trait ScIdList extends ScalaPsiElement {
  def fieldIds: Seq[ScFieldId]
}