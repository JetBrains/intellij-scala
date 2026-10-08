package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
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
    val key             = BaseTypeKey(t, clazz)
    val cache           = baseTypeCache(t.projectContext.project)
    val resultInContext = Option(cache.get(key)).getOrElse(new ContextDependent[Option[ScType]]())
    resultInContext.get.getOrElse {
      // A re-entrant query for the same key is a cyclic base-type graph: no base type, as
      // conformance answers `Left`. The guard also keeps the enclosing results out of caches.
      baseTypeGuard.doPreventingRecursion(key) {
        val stackStamp              = RecursionManager.markStack()
        val (value, valueInContext) = resultInContext.updatedUsing(ctx => baseTypeUncached(t, clazz)(using ctx))
        if (stackStamp.mayCacheNow() && isCacheable(t)) cache.put(key, valueInContext)
        value
      }.flatten
    }
  }

  private def baseTypeUncached(t: ScType, clazz: PsiClass)(implicit context: Context): Option[ScType] = {
    val sameClass = (Iterator(t) ++ iterator(t)).filter(_.extractClass.contains(clazz)).toList
    if (sameClass.isEmpty) None
    else Some(mergeSameClass(sameClass, clazz))
  }

  private final case class BaseTypeKey(t: ScType, clazz: PsiClass)

  private val baseTypeGuard = RecursionManager.RecursionGuard[BaseTypeKey, Option[ScType]]("BaseTypes.baseType.guard")

  /** Inference variables aren't cached (checked on a miss only: no cached key contains one): they are per inference session, and `ScAbstractType`'s equality ignores its bounds. */
  private def isCacheable(t: ScType): Boolean =
    !t.subtypeExists {
      case _: UndefinedType | _: ScAbstractType => true
      case _                                    => false
    }

  private def baseTypeCache(project: Project): ConcurrentMap[BaseTypeKey, ContextDependent[Option[ScType]]] =
    project.getService(classOf[BaseTypeCacheService]).cache

  /** Cleared with the conformance cache, by [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager]]. */
  def clearCache(project: Project): Unit = baseTypeCache(project).clear()

  @Service(Array(Service.Level.PROJECT))
  private final class BaseTypeCacheService {
    val cache: ConcurrentMap[BaseTypeKey, ContextDependent[Option[ScType]]] = new ConcurrentHashMap()
  }

  /**
   * Merge several base types of the same class into one — scalac's
   * `mergePrefixAndArgs`: combine arguments per position by the class's variance
   * (covariant -> glb, contravariant -> lub, invariant -> kept when equivalent, else
   * an existential bounded by their glb and lub: `I[Dog] with I[Cat]` has base type
   * `I[_1] forSome { type _1 >: Cat with Dog <: Animal }`). IntelliJ's plain
   * `glb` does NOT do this — for incomparable args it yields the intersection of
   * the applied types (`Box[Dog] with Box[Cat]`) rather than the merge
   * (`Box[Dog with Cat]`), so we do it explicitly.
   */
  private def mergeSameClass(types: Seq[ScType], clazz: PsiClass)(implicit context: Context): ScType = {
    val distinct = types.distinct
    if (distinct.lengthCompare(1) <= 0) distinct.head
    else clazz match {
      case owner: ScTypeParametersOwner =>
        val variances = owner.typeParameters.map(_.variance)
        // Merge all applications of `clazz` at once, position by position: a pairwise reduce would
        // turn the first merge into an existential, which no longer matches the next application.
        val (applied, other) = distinct.partitionMap {
          case p: ParameterizedType if p.typeArguments.sizeCompare(variances) == 0 => Left(p)
          case t                                                                   => Right(t)
        }
        val merged = applied match {
          case Seq()       => None
          case Seq(single) => Some(single)
          case several =>
            val wildcards = List.newBuilder[ScExistentialArgument]
            val args = variances.indices.map { i =>
              val v  = variances(i)
              val ts = several.map(_.typeArguments(i))
              if (v.isCovariant) ts.reduce(_ glb _)
              else if (v.isContravariant) ts.reduce(_ lub _)
              else if (ts.tail.forall(_.equiv(ts.head))) ts.head
              else {
                val w = ScExistentialArgument(s"_$$${i + 1}", Nil, ts.reduce(_ glb _), ts.reduce(_ lub _))
                wildcards += w
                w
              }
            }
            val app = ScParameterizedType(several.head.designator, args)
            val ws  = wildcards.result()
            Some(if (ws.isEmpty) app else ScExistentialType(app, Some(ws)))
        }
        (merged.toSeq ++ other).reduce(_ glb _)
      case _ => distinct.reduce(_ glb _)
    }
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
    // `seen` breaks singleton-widening cycles (e.g. an object whose
    // designatorSingletonType is its own type): a repeat falls back to ClassType.
    @tailrec
    def go(t: ScType, seen: Set[ScType]): Seq[ScType] = t match {
      case IsTypeAlias(ta, s) if !ta.isEffectivelyOpaque && !seenAliases.contains(ta) =>
        seenAliases += ta.physical
        ta.aliasedType match {
          case Right(aliased) => go(s(aliased), seen)
          case _              => Seq.empty
        }
      case ScThisType(clazz)                       =>
        // `X.this` also conforms to `X`'s self type, so the self type's bases are
        // bases of it too (needed by the anchored walk in ThisTypeSubstitution).
        (clazz.`type`().toOption, clazz.selfType) match {
          case (Some(ct), Some(st)) => go(ScCompoundType(Seq(ct, st))(using tp.projectContext), seen)
          case (Some(ct), None)     => go(ct, seen)
          case (None, Some(st))     => go(st, seen)
          case (None, None)         => Seq.empty
        }
      case JavaArrayType(_)                        => Seq(tp.projectContext.stdTypes.Any)
      case ScCompoundType(comps, _, _)             => comps
      case ScAndType(lhs, rhs)                     => Seq(lhs, rhs)
      case SingletonUnderlying(underlying) if !seen.contains(underlying) =>
        // A singleton path type (e.g. `x.type` for `x: ValDef`) is not itself a
        // class/object designator, so ClassType never fires for it. Widen to the
        // declared/resolved type of the underlying value (scalac's `underlying`,
        // used by SingleType.baseTypeSeq) so its base classes (e.g. ValDef ->
        // ValOrDefDef -> Tree) are reachable through the singleton prefix.
        go(underlying, seen + t)
      case ClassType(c, subst)                     => declaredSuperTypes(c, subst)
      case _                                       => Seq.empty
    }
    go(tp, Set.empty)
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
  // Classes keep the order in which the walk first reaches them.
  private def reduce(typesIt: Iterator[ScType])(implicit context: Context): Seq[ScType] = {
    val byClass = mutable.LinkedHashMap.empty[PsiClass, mutable.ArrayBuffer[ScType]]
    typesIt.foreach(t => t.extractClass.foreach(c => byClass.getOrElseUpdate(c, mutable.ArrayBuffer.empty) += t))
    byClass.iterator.map { case (clazz, ts) => mergeSameClass(ts.toSeq, clazz) }.toList
  }

  private object SingletonUnderlying {
    def unapply(tp: ScType): Option[ScType] = tp match {
      case owner: DesignatorOwner => owner.designatorSingletonType
      case _                      => None
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
}
