package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeInTypeSystem, TypeVisitor, ValueType}

/**
  * @author adkozlov
  */
trait DottyType extends TypeInTypeSystem {
  implicit val typeSystem = DottyTypeSystem
}

// is value type?
case object DottyNoType extends DottyType with ValueType {
  override def visitType(visitor: TypeVisitor) = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitNoType(this)
    case _ =>
  }

  override def isFinalType = true
}

trait DottyConstantType extends DottyType