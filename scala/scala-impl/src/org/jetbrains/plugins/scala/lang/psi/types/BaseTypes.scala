package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

object BaseTypes {

  def iterator(tp: ScType)(implicit context: Context): Iterator[ScType] = dfs(tp)

  /**
   * Iterator over the direct super types of `tp` — the "first layer" of base
   * types. For a class type this yields the declared parents (with substitution
   * applied). For a compound / intersection type it yields the components.
   * For a type alias / type parameter / this-type / existential, `tp` is first
   * resolved to the underlying type and then that type's direct supers are
   * returned.
   */
  def direct(tp: ScType)(implicit context: Context): Iterator[ScType] =
    supersOf(tp, mutable.Set.empty).iterator

  /**
   * Iterator over base types of `tp` in depth-first, declaration-order
   * traversal. The first-declared parent is explored fully before moving to
   * siblings. Duplicate types are yielded only once.
   */
  def dfs(tp: ScType)(implicit context: Context): Iterator[ScType] = new Iterator[ScType] {
    private val stack       = mutable.Stack.empty[ScType]
    private val seenAliases = mutable.Set.empty[ScTypeAlias]
    private val seenTypes   = mutable.Set.empty[ScType]

    // Seed with `tp`'s supers, pushed in reverse order so that pops yield
    // declaration order.
    supersOf(tp, seenAliases).reverseIterator.foreach(pushIfNew)

    override def hasNext: Boolean = stack.nonEmpty
    override def next(): ScType = {
      val t = stack.pop()
      supersOf(t, seenAliases).reverseIterator.foreach(pushIfNew)
      t
    }

    private def pushIfNew(t: ScType): Unit = {
      if (seenTypes.add(t)) stack.push(t)
    }
  }

  /**
   * Iterator over base types of `tp` in breadth-first, declaration-order
   * traversal. All same-level parents are visited before recursing into their
   * supers. Duplicate types are yielded only once.
   */
  def bfs(tp: ScType)(implicit context: Context): Iterator[ScType] = new Iterator[ScType] {
    private val queue       = mutable.Queue.empty[ScType]
    private val seenAliases = mutable.Set.empty[ScTypeAlias]
    private val seenTypes   = mutable.Set.empty[ScType]

    supersOf(tp, seenAliases).foreach(enqueueIfNew)

    override def hasNext: Boolean = queue.nonEmpty
    override def next(): ScType = {
      val t = queue.dequeue()
      supersOf(t, seenAliases).foreach(enqueueIfNew)
      t
    }

    private def enqueueIfNew(t: ScType): Unit = {
      if (seenTypes.add(t)) queue.enqueue(t)
    }
  }

  /**
   * Iterator over the class linearization of `tp`, following the Scala
   * language specification:
   * {{{
   *   Lin(C) = C :: dedupRightmost(Lin(C_n) ++ ... ++ Lin(C_1))
   * }}}
   * where `C_1, ..., C_n` are `C`'s parents in declaration order and
   * `dedupRightmost` keeps only the last occurrence of each class. This is
   * what Scala 3's `baseClasses` produces and is what dotc uses when
   * constraining higher-kinded type variables (see
   * `dotty.tools.dotc.core.TypeComparer#compareAppliedType2.canInstantiate`).
   *
   * The returned iterator does **not** yield `tp` itself.
   *
   * Implementation: do a DFS in *reverse* declaration order (last-declared
   * parent first), track membership in the current path only (allowing shared
   * ancestors to be re-visited on each branch), then apply
   * [[dedupRightmostByClass]] so each class ends up at its right-most position
   * — which is exactly where the linearization formula's right-precedence `+l`
   * merge would put it.
   */
  def linearize(tp: ScType)(implicit context: Context): Iterator[ScType] = {
    val out         = ArraySeq.newBuilder[ScType]
    val onPath      = mutable.Set.empty[PsiClass]
    val seenAliases = mutable.Set.empty[ScTypeAlias]

    def rec(t: ScType): Unit = {
      val cls = t.extractClass
      if (cls.exists(onPath.contains)) return
      cls.foreach(onPath += _)
      out += t
      supersOf(t, seenAliases).reverseIterator.foreach(rec)
      cls.foreach(onPath -= _)
    }

    supersOf(tp, seenAliases).reverseIterator.foreach(rec)
    dedupRightmostByClass(out.result())
  }

  def get(t: ScType)(implicit context: Context): Seq[ScType] = reduce(dfs(t))

  /**
   * Returns the direct super types of `tp` in declaration order, resolving
   * aliases / type parameters / this-types / existentials to their underlying
   * types first. `seenAliases` tracks aliases already unwrapped to break
   * cycles in recursive alias definitions.
   *
   * This is the single source of truth for "children of a type" in the base
   * type graph; the four public iterators differ only in how they schedule
   * these children.
   */
  private def supersOf(tp: ScType, seenAliases: mutable.Set[ScTypeAlias])
                      (implicit context: Context): Seq[ScType] = {
    @tailrec
    def go(t: ScType): Seq[ScType] = t match {
      case IsTypeAlias(ta, s) if !ta.isEffectivelyOpaque && !seenAliases.contains(ta) =>
        seenAliases += ta.physical
        ta.aliasedType match {
          case Right(aliased) => go(s(aliased))
          case _              => Seq.empty
        }
      case ScThisType(clazz)                       => clazz.`type`().toOption match {
        case Some(inner) => go(inner)
        case None        => Seq.empty
      }
      case JavaArrayType(_)                        => Seq(tp.projectContext.stdTypes.Any)
      case ScCompoundType(comps, _, _)             => comps
      case ScAndType(lhs, rhs)                     => Seq(lhs, rhs)
      case ClassType(c, subst)                     => declaredSuperTypes(c, subst)
      case _                                       => Seq.empty
    }
    go(tp)
  }

  private def declaredSuperTypes(c: PsiClass, subst: ScSubstitutor): Seq[ScType] = c match {
    case td: ScTemplateDefinition => td.superTypes.map(subst)
    case _ =>
      ArraySeq.unsafeWrapArray(c.getSuperTypes).map { st =>
        subst(st.toScType()(c)) match {
          case exist: ScExistentialType => exist.quantified
          case other                    => other
        }
      }
  }

  private def dedupRightmostByClass(seq: Seq[ScType]): Iterator[ScType] = {
    val seenClasses = mutable.Set.empty[PsiClass]
    // Walk right-to-left, keeping the first occurrence encountered (which
    // corresponds to the *rightmost* occurrence in `seq`).
    seq.reverseIterator
      .filter(t => t.extractClass.forall(seenClasses.add))
      .to(mutable.ArrayBuffer)
      .reverseIterator
  }

  private def reduce(typesIt: Iterator[ScType])(implicit context: Context): Seq[ScType] = {
    val res = mutable.HashMap.empty[PsiClass, ScType]
    val all = mutable.HashMap.empty[PsiClass, mutable.Set[ScType]]
    while (typesIt.hasNext) {
       val t = typesIt.next()
      t.extractClass match {
        case Some(c) =>
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => !ts.exists(t.conforms(_))
          }
          if (isBest) {
            res += c -> t
          }
          all.getOrElseUpdate(c, mutable.Set.empty) += t
        case None => //not a class type
      }
    }
    res.values.toList
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
}
