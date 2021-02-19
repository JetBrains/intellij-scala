package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/
trait ScTupleTypeElementBase extends ScDesugarizableToParametrizedTypeElement { this: ScTupleTypeElement =>
  override protected val typeName = "TupleType"

  def typeList: ScTypes = findChild[ScTypes].get

  def components: Seq[ScTypeElement] = typeList.types

  override def desugarizedText: String = {
    val componentsTexts = components.map(_.getText)
    s"_root_.scala.Tuple${componentsTexts.length}${componentsTexts.mkString("[", ",", "]")}"
  }
}

abstract class ScTupleTypeElementCompanion {
  def unapplySeq(e: ScTupleTypeElement): Some[Seq[ScTypeElement]] = Some(e.components)
}