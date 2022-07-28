package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScTupleTypeElement extends ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "TupleType"

  def typeList: ScTypes = findChild[ScTypes].get

  def components: Seq[ScTypeElement] = typeList.types

  override def desugarizedText: String = {
    val componentsTexts = components.map(_.getText)
    s"_root_.scala.Tuple${componentsTexts.length}${componentsTexts.mkString("[", ",", "]")}"
  }
}

object ScTupleTypeElement {
  def unapplySeq(e: ScTupleTypeElement): Some[Seq[ScTypeElement]] = Some(e.components)
}