package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdTypes, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScOrType, ScParameterizedType, ScType}

import scala.annotation.switch

object TupleIntrinsics {
  def tupleOp(opName: String, operands: Seq[ScType])(implicit project: Project): Option[ScType] = {
    implicit val elementScope: ElementScope = ElementScope(project)

    @inline def mkTuple(comps: Seq[ScType]): ScType =
      TupleType(comps, scala3 = true)

    (opName: @switch) match {
      case "Append" =>
        operands match {
          case Seq(TupleType(comps), elem) => Some(mkTuple(comps :+ elem))
          case _ => None
        }
      case "Head" =>
        operands match {
          case Seq(TupleType.withTail(comps, _)) => comps.headOption
          case _ => None
        }
      case "Init" =>
        operands match {
          case Seq(TupleType(comps)) => Some(mkTuple(comps.init))
          case _ => None
        }
      case "Tail" =>
        operands match {
          case Seq(TupleType.withTail(comps, tail)) => Some(TupleType.withTail(comps.tail, tail))
          case _ => None
        }
      case "Last" =>
        operands match {
          case Seq(TupleType(comps)) => comps.lastOption
          case _ => None
        }
      case "Concat" | "++" =>
        operands match {
          case Seq(TupleType(fst), TupleType.withTail(snd, tail)) => Some(TupleType.withTail(fst ++ snd, tail))
          case _ => None
        }
      case "Elem" =>
        operands match {
          case Seq(TupleType.withTail(comps, _), IntValue(i)) => comps.lift(i)
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
            Some(mkTuple(comps.map(elem => ScParameterizedType(f, Seq(elem)))))
          case _ => None
        }
      case "FlatMap" =>
        operands match {
          case Seq(TupleType(comps), f) =>
            Some(mkTuple(comps.flatMap { elem =>
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
            Some(mkTuple(comps.filter { elem =>
              ScParameterizedType(f, Seq(elem)).removeAliasDefinitions() match {
                case BooleanValue(false) => false
                case _ => true
              }
            }))
          case _ => None
        }
      case "Zip" =>
        operands match {
          case Seq(TupleType(fst), TupleType(snd)) => Some(mkTuple(fst.zip(snd).map { case (a, b) => mkTuple(Seq(a, b)) }))
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
          case Seq(TupleType(comps)) => Some(mkTuple(comps.reverse))
          case _ => None
        }
      case "Take" =>
        operands match {
          case Seq(TupleType.withTail(comps, tail), IntValue(i)) if tail.isEmpty || i <= comps.size => Some(mkTuple(comps.take(i)))
          case _ => None
        }
      case "Drop" =>
        operands match {
          case Seq(TupleType.withTail(comps, tail), IntValue(i)) if tail.isEmpty || i <= comps.size => Some(TupleType.withTail(comps.drop(i), tail))
          case _ => None
        }
      case "Split" =>
        operands match {
          case Seq(TupleType.withTail(comps, tail), IntValue(i)) if tail.isEmpty || i <= comps.size =>
            val (fst, snd) = comps.splitAt(i)
            Some(mkTuple(Seq(mkTuple(fst), TupleType.withTail(snd, tail))))
          case _ => None
        }
      case "Union" =>
        operands match {
          case Seq(TupleType(comps)) =>
            Some(
              comps
                .reduceOption[ScType] { case (a, b) => ScOrType(a, b) }
                .getOrElse(StdTypes.instance.Nothing)
            )
          case _ => None
        }
      case "Contains" =>
        operands match {
          case Seq(TupleType.withTail(comps, tail), searched) =>
            val found = comps.exists(_.conforms(searched))
            if (found || tail.isEmpty) Some(BooleanValue(found))
            else None // we neither found the value nor could we search the whole tuple
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
