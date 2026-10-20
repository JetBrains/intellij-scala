package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, TupleType}

import scala.annotation.switch

object NamedTupleDecompositionIntrinsics {
  def namedTupleDecompositionOp(opName: String, operands: Seq[ScType])(implicit project: Project): Option[ScType] = {
    implicit val elementScope: ElementScope = ElementScope(project)

    (opName: @switch) match {
      case "Names" =>
        operands match {
          case Seq(NamedTupleType(comps)) => Some(TupleType(comps.map(_._1), scala3 = true))
          case _ => None
        }
      case "DropNames" =>
        operands match {
          case Seq(NamedTupleType(comps)) => Some(TupleType(comps.map(_._2), scala3 = true))
          case _ => None
        }
      case _ =>
        None
    }
  }
}
