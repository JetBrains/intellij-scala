package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScOrType, ScParameterizedType, ScType}

import scala.annotation.switch

object TupleIntrinsics {
  def tupleOp(opName: String, operands: Seq[ScType])(implicit project: Project): Option[ScType] = {
    implicit val elementScope: ElementScope = ElementScope(project)

    (opName: @switch) match {
      case "*:" =>
        operands match {
          case Seq(elem, TupleType(comps)) => Some(TupleType(elem +: comps))
          case _ => None
        }
      case "Append" =>
        operands match {
          case Seq(TupleType(comps), elem) => Some(TupleType(comps :+ elem))
          case _ => None
        }
      case "Head" =>
        operands match {
          case Seq(TupleType(comps)) => comps.headOption
          case _ => None
        }
      case "Init" =>
        operands match {
          case Seq(TupleType(comps)) => Some(TupleType(comps.init))
          case _ => None
        }
      case "Tail" =>
        operands match {
          case Seq(TupleType(comps)) => Some(TupleType(comps.tail))
          case _ => None
        }
      case "Last" =>
        operands match {
          case Seq(TupleType(comps)) => comps.lastOption
          case _ => None
        }
      case "Concat" =>
        operands match {
          case Seq(TupleType(fst), TupleType(snd)) => Some(TupleType(fst ++ snd))
          case _ => None
        }
      case "Elem" =>
        operands match {
          case Seq(TupleType(comps), IntValue(i)) => comps.lift(i)
          case _ => None
        }
      case "Size" =>
        operands match {
          case Seq(TupleType(comps)) => Some(IntValue(comps.size))
          case _ => None
        }
      case "Fold" =>
        operands match {
          case Seq(TupleType(comps), z, f) =>
            Some(comps.foldRight(z) {
              case (elem, acc) =>
                ScParameterizedType(f, Seq(elem, acc))
            })
          case _ => None
        }
      case "Map" =>
        operands match {
          case Seq(TupleType(comps), f) =>
            Some(TupleType(comps.map(elem => ScParameterizedType(f, Seq(elem)))))
          case _ => None
        }
      case "FlatMap" =>
        operands match {
          case Seq(TupleType(comps), f) =>
            Some(TupleType(comps.flatMap { elem =>
              ScParameterizedType(f, Seq(elem)).removeAliasDefinitions() match {
                case TupleType(comps) => comps
                case ty => Seq(ty)
              }
            }))
          case _ => None
        }
      case "Filter" =>
        operands match {
          case Seq(TupleType(comps), f) =>
            Some(TupleType(comps.filter { elem =>
              ScParameterizedType(f, Seq(elem)).removeAliasDefinitions() match {
                case BooleanValue(false) => false
                case _ => true
              }
            }))
          case _ => None
        }
      case "Zip" =>
        operands match {
          case Seq(TupleType(fst), TupleType(snd)) => Some(TupleType(fst.zip(snd).map { case (a, b) => TupleType(Seq(a, b)) }))
          case _ => None
        }
      case "InverseMap" =>
        // TODO: you know how to do this? Go then do it!!!
        //operands match {
        //  case Seq(TupleType(comps), f) =>
        //    val tester = UndefinedType.apply()
        //    val testerF = ScParameterizedType(f, )
        //    f.removeAliasDefinitions() match {
        //      case ty@ScTypePolymorphicType(_, Seq(tparam)) =>
        //        Some(TupleType(comps.map { elem =>
        //          elem.conformanceSubstitutor(ty) match {
        //            case Some(subst) => subst(tparam)
        //            case
        //          }
        //        }))
        //      case _ => None
        //    }
        //  case _ => None
        //}
        None
      case "IsMappedBy" =>
        // todo: this as well
        None
      case "Reverse" =>
        operands match {
          case Seq(TupleType(comps)) => Some(TupleType(comps.reverse))
          case _ => None
        }
      case "Take" =>
        operands match {
          case Seq(TupleType(comps), IntValue(i)) => Some(TupleType(comps.take(i)))
          case _ => None
        }
      case "Drop" =>
        operands match {
          case Seq(TupleType(comps), IntValue(i)) => Some(TupleType(comps.drop(i)))
          case _ => None
        }
      case "Split" =>
        operands match {
          case Seq(TupleType(comps), IntValue(i)) =>
            val (fst, snd) = comps.splitAt(i)
            Some(TupleType(Seq(TupleType(fst), TupleType(snd))))
          case _ => None
        }
      case "Union" =>
        operands match {
          case Seq(TupleType(fst), TupleType(snd)) =>
            Some(TupleType(fst.zip(snd).map { case (a, b) => ScOrType(a, b) }))
          case _ => None
        }
      case "Contains" =>
        operands match {
          case Seq(TupleType(comps), searched) =>
            Some(BooleanValue(comps.exists(_.conforms(searched))))
          case _ =>
            None
        }
      case "Disjoint" =>
        operands match {
          case Seq(TupleType(fst), TupleType(snd)) =>
            Some(BooleanValue(fst.forall(a => snd.forall(b => !b.conforms(a)))))
          case _ =>
            None
        }
      case _ =>
        None
    }
  }
}
