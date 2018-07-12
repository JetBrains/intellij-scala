package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.annotation.tailrec
import scala.collection.mutable

object BaseTypes {

  def iterator(tp: ScType): Iterator[ScType] = new BaseTypesIterator(tp)

  def get(t: ScType): Seq[ScType] = reduce(iterator(t).toList)

  private def reduce(types: Seq[ScType]): Seq[ScType] = {
    val res = new mutable.HashMap[PsiClass, ScType]
    object all extends mutable.HashMap[PsiClass, mutable.Set[ScType]] with mutable.MultiMap[PsiClass, ScType]
    val iterator = types.iterator
    while (iterator.hasNext) {
       val t = iterator.next()
      t.extractClass match {
        case Some(c) =>
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => !ts.exists(t.conforms(_))
          }
          if (isBest) res += ((c, t))
          all.addBinding(c, t)
        case None => //not a class type
      }
    }
    res.values.toList
  }
}

private class BaseTypesIterator(tp: ScType) extends Iterator[ScType] {
  import tp.projectContext

  private val initialCapacity = 4
  private val queue = new util.ArrayDeque[ScType](initialCapacity)
  private val visitedAliases = mutable.Set.empty[ScTypeAlias]
  private val seenTypes = mutable.Set.empty[ScType]

  enqueueSupers(tp)

  override def hasNext: Boolean = !queue.isEmpty

  override def next(): ScType = {
    val tp = queue.pollLast()
    enqueueSupers(tp)
    tp
  }

  private def enqueue(tp: ScType): Unit = {
    if (!seenTypes.contains(tp)) {
      queue.addFirst(tp)
      seenTypes += tp
    }
  }

  private def enqueueSupersForClass(c: PsiClass, substitutor: ScSubstitutor = ScSubstitutor.empty): Unit = {
    val superTypes = c match {
      case td: ScTemplateDefinition => td.superTypes
      case _ => c.getSuperTypes.toSeq.map(_.toScType())
    }
    superTypes.foreach { st =>
      val substed = substitutor.subst(st)
      enqueue(substed)
    }
  }

  @tailrec
  private def enqueueSupers(t: ScType): Unit = {
    t match {
      case InterestedIn(r) => enqueueSupers(r)
      case ClassType(c, subst) => enqueueSupersForClass(c, subst)
      case JavaArrayType(_) => enqueue(Any)
      case ScCompoundType(comps, _, _) => comps.foreach(enqueue)
      case _ =>
    }
  }

  private object IsTypeAlias {
    def unapply(tp: ScType): Option[(ScTypeAliasDefinition, ScSubstitutor)] = tp match {
      case ScDesignatorType(ta: ScTypeAliasDefinition) => Some((ta, ScSubstitutor.empty))
      case ScProjectionType.withActual((ta: ScTypeAliasDefinition, actualSubst)) => Some((ta, actualSubst))
      case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
        val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
        Some((ta, genericSubst))
      case ParameterizedType(ScProjectionType.withActual(ta: ScTypeAliasDefinition, actualSubst), args) =>
        val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
        val s = actualSubst.followed(genericSubst)
        Some((ta, s))
      case _ => None
    }
  }

  private object ClassType {
    def unapply(tp: ScType): Option[(PsiClass, ScSubstitutor)] = tp match {
      case ScDesignatorType(c: PsiClass) => Some((c, ScSubstitutor.empty))
      case p : ScParameterizedType =>
        p.designator.extractClass match {
          case Some(clazz) => Some((clazz, p.substitutor))
          case _ => None
        }
      case ScProjectionType.withActual(c: PsiClass, subst) =>
        Some((c, subst))
      case _ => None
    }
  }

  private object InterestedIn {
    def unapply(tp: ScType): Option[ScType] = {
      if (seenTypes.contains(tp)) return None
      seenTypes += tp

      tp match {
        case IsTypeAlias(ta, s) =>
          if (!visitedAliases.contains(ta)) {
            visitedAliases += ta.physical
            ta.aliasedType match {
              case Right(aliased) => Some(s.subst(aliased))
              case _ => None
            }
          }
          else None
        case ScThisType(clazz) =>
          clazz.getTypeWithProjections().toOption
        case tpt: TypeParameterType =>
          Some(tpt.upperType)
        case ScExistentialArgument(_, Nil, _, upper) =>
          Some(upper)
        case ex: ScExistentialType =>
          Some(ex.quantified.unpackedType)
        case _ => None
      }
    }
  }
}