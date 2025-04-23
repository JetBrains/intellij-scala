package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

import scala.annotation.switch

object NamedTupleIntrinsics {
  def namedTupleOp(opName: String, operands: Seq[ScType])(implicit project: Project): Option[ScType] = {
    implicit val elementScope: ElementScope = ElementScope(project)

    (opName: @switch) match {
      case "Size" =>
        operands match {
          case Seq(NamedTupleType(comps)) => Some(IntValue(comps.size))
          case _ => None
        }
      case "Elem" =>
        operands match {
          case Seq(NamedTupleType(comps), IntValue(i)) => comps.lift(i).map { case (_, tpe) => tpe }
          case _ => None
        }
      case "Head" =>
        operands match {
          case Seq(NamedTupleType(comps)) => comps.headOption.map { case (_, tpe) => tpe }
          case _ => None
        }
      case "Last" =>
        operands match {
          case Seq(NamedTupleType(comps)) => comps.lastOption.map { case (_, tpe) => tpe }
          case _ => None
        }
      case "Tail" =>
        operands match {
          case Seq(NamedTupleType(comps)) => Some(NamedTupleType(comps.tail))
          case _ => None
        }
      case "Init" =>
        operands match {
          case Seq(NamedTupleType(comps)) => Some(NamedTupleType(comps.init))
          case _ => None
        }
      case "Take" =>
        operands match {
          case Seq(NamedTupleType(comps), IntValue(i)) => Some(NamedTupleType(comps.take(i)))
          case _ => None
        }
      case "Drop" =>
        operands match {
          case Seq(NamedTupleType(comps), IntValue(i)) => Some(NamedTupleType(comps.drop(i)))
          case _ => None
        }
      case "Split" =>
        operands match {
          case Seq(NamedTupleType(comps), IntValue(i)) =>
            val (fst, snd) = comps.splitAt(i)
            Some(TupleType(Seq(NamedTupleType(fst), NamedTupleType(snd)), scala3 = true))
          case _ => None
        }
      case "Concat" =>
        operands match {
          case Seq(NamedTupleType(fst), NamedTupleType(snd)) => Some(NamedTupleType(fst ++ snd))
          case _ => None
        }
      case "Map" =>
        operands match {
          case Seq(NamedTupleType(nt), f) =>
            val mapped = nt.map {
              case (name, ty) =>
                (name, ScParameterizedType(f, Seq(ty)).removeAliasDefinitions())
            }
            Some(NamedTupleType(mapped))
          case _ =>
            None
        }
      case "Reverse" =>
        operands match {
          case Seq(NamedTupleType(nt)) => Some(NamedTupleType(nt.reverse))
          case _ => None
        }
      case "Zip" =>
        operands match {
          case Seq(NamedTupleType(fst), NamedTupleType(snd)) =>
            val namesEqual = fst.corresponds(snd) {
              case ((n1, _), (n2, _)) => n1 == n2
            }

            namesEqual.option {
              val zipped = fst.zip(snd).map {
                case ((name, t1), (_, t2)) => (name, TupleType(Seq(t1, t2), scala3 = true))
              }
              NamedTupleType(zipped)
            }
          case _ => None
        }
      case "From" =>
        operands match {
          case Seq(nt@NamedTupleType(_)) => Some(nt)
          case Seq(t@TupleType(_)) => Some(t)
          case Seq(ty) =>
            ty.extractClassType match {
              case Some((clazz: ScClass, subst)) if clazz.isCase =>
                clazz.allClauses.headOption
                  .map {
                    clause =>
                      val comps =
                        clause.parameters.map(p => StringValue(p.name) -> subst(p.`type`().getOrNothing))
                      NamedTupleType(comps)
                  }

              case _ => None
            }
          case _ => None
        }
      case "Names" | "DropNames" =>
        // they are imported into the NamedTuple object
        NamedTupleDecompositionIntrinsics.namedTupleDecompositionOp(opName, operands)
      case _ =>
        None
    }
  }
}
