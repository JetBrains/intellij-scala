package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
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
   * The base type of `t` at class `clazz`, the analogue of scalac's `t baseType clazz`
   * (used by `AsSeenFromMap`). When `t` reaches `clazz` through several parents with
   * different arguments, the contributions are merged ([[mergeSameClass]]), so the
   * result is deterministic, unlike `iterator(t).find(_.extractClass.contains(clazz))`.
   */
  def baseType(t: ScType, clazz: PsiClass)(implicit context: Context): Option[ScType] = {
    val sameClass = (Iterator(t) ++ iterator(t)).filter(_.extractClass.contains(clazz)).toList
    if (sameClass.isEmpty) None
    else Some(mergeSameClass(sameClass, clazz))
  }

  /**
   * Merge several base types of the same class into one — scalac's
   * `mergePrefixAndArgs`: combine arguments per position by the class's variance
   * (covariant -> glb, contravariant -> lub, invariant -> kept). IntelliJ's plain
   * `glb` does NOT do this — for incomparable args it yields the intersection of
   * the applied types (`Box[Dog] with Box[Cat]`) rather than the merge
   * (`Box[Dog with Cat]`), so we do it explicitly.
   */
  private def mergeSameClass(types: Seq[ScType], clazz: PsiClass)(implicit context: Context): ScType =
    if (types.lengthCompare(1) <= 0) types.head
    else clazz match {
      case owner: ScTypeParametersOwner =>
        val variances = owner.typeParameters.map(_.variance)
        types.reduce { (a, b) =>
          (a, b) match {
            case (ParameterizedType(designator, as), ParameterizedType(_, bs))
                if as.sizeCompare(bs) == 0 && as.sizeCompare(variances) == 0 =>
              val merged = variances.indices.map { i =>
                val v = variances(i)
                if (v.isCovariant) as(i).glb(bs(i))
                else if (v.isContravariant) as(i).lub(bs(i))
                else as(i) // invariant: contributions are equivalent
              }
              ScParameterizedType(designator, merged)
            case _ => a.glb(b)
          }
        }
      case _ => types.reduce((a, b) => a.glb(b))
    }

  /**
   * Ordered, deduplicated, same-symbol-merged base type sequence — one entry per
   * base class, more-derived classes first (an order consistent with subtyping).
   * Mirrors scalac's `baseTypeSeq` (modulo the exact symbol-id tie-break among
   * unrelated classes, which is deterministic here but by base-class count + name).
   */
  def baseTypeSeq(t: ScType)(implicit context: Context): Seq[ScType] = {
    val all = (Iterator(t) ++ iterator(t)).toList
    val perClass = all.flatMap(tp => tp.extractClass.map(_ -> tp)).groupBy(_._1)
    val merged = perClass.toSeq.map { case (c, ps) => mergeSameClass(ps.map(_._2), c) }
    merged.sortBy { tp =>
      val name = tp.extractClass.flatMap(c => Option(c.getQualifiedName)).getOrElse("")
      (-baseClassCount(tp), name)
    }
  }

  /** Number of transitive base classes — a subtyping-consistent ordering key
   *  (a subtype has a superset of its supertype's base classes). */
  private def baseClassCount(t: ScType)(implicit context: Context): Int =
    t.extractClass match {
      case Some(c) =>
        val seen = mutable.Set.empty[PsiClass]
        def go(c: PsiClass): Unit = if (seen.add(c)) c.getSupers.foreach(go)
        go(c)
        seen.size
      case None => 0
    }

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
      case ScThisType(clazz)                       =>
        // `X.this` also conforms to `X`'s self type, so the self type's bases are
        // bases of it too (needed by the anchored walk in ThisTypeSubstitution).
        (clazz.`type`().toOption, clazz.selfType) match {
          case (Some(ct), Some(st)) => go(ScCompoundType(Seq(ct, st))(tp.projectContext))
          case (Some(ct), None)     => go(ct)
          case (None, Some(st))     => go(st)
          case (None, None)         => Seq.empty
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
        subst(st.toScType()(using c)) match {
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

  // One base type per class. Same-class contributions are *merged*
  // (mergeSameClass) rather than the previous "keep the most specific arm", so a
  // class reached via several paths with different arguments yields the variance
  // merge (e.g. Box[Dog with Cat]) instead of a single arm (Box[Dog] or Box[Cat]).
  private def reduce(typesIt: Iterator[ScType])(implicit context: Context): Seq[ScType] =
    typesIt.toList
      .flatMap(t => t.extractClass.map(_ -> t))
      .groupBy(_._1)
      .iterator
      .map { case (clazz, ps) => mergeSameClass(ps.map(_._2), clazz) }
      .toList

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
