package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.jetbrains.plugins.scala.lang.psi.types.api.Singleton
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * The single place that implements the widening rules of the Scala compilers.
 *
 * Widening happens whenever a type is ''inferred'' instead of being written down: singleton types
 * (literal types, `x.type`, singleton enum cases) are replaced by their underlying types, so that
 * for instance `val x = 1` is an `Int` and not a `1`.
 *
 * Whether and how much a type is widened depends on the type itself, on the expected type and on
 * the Scala version:
 *  - Scala 2: `scala.tools.nsc.typechecker.Namers.widenIfNecessary`
 *  - Scala 3: `dotty.tools.dotc.typer.Namer.inferredResultType`
 *             together with `dotty.tools.dotc.core.ConstraintHandling.widenInferred`
 */
object Widening {

  /** The kind of definition an inferred type is computed for, see [[widenInferredDefinitionType]]. */
  sealed trait DefinitionKind
  object DefinitionKind {
    /** A `val` (including a `lazy val`), whose type is only widened as far as Scala 2's `deconst` goes. */
    case object Val extends DefinitionKind

    /** A constant value definition (`final val`), which keeps its literal type. */
    case object ConstantVal extends DefinitionKind

    /** A `var`, which is always widened completely in Scala 2. */
    case object Var extends DefinitionKind

    /** A `def`, which is always widened completely in Scala 2. */
    case object Def extends DefinitionKind
  }

  /**
   * Widens a singleton type to its underlying non-singleton base type by applying one or more
   * `underlying` dereferences. Identity for all other types.
   *
   * Corresponds to `Type.widen` in the Scala 3 compiler and to `Type.widen` in the Scala 2 compiler.
   *
   * @example {{{
   *   val c: Any = ???
   *   val x: c.type = c
   *   // <x.type>.widenSingleton == Any
   * }}}
   */
  def widenSingleton(tpe: ScType): ScType = widen(tpe, stopAtLiteralType = false)

  /**
   * Widens references to terms to their declared types, but stops at literal types.
   *
   * Corresponds to `Type.widenTermRefExpr` in the Scala 3 compiler, which is applied to the type of
   * a right hand side before it becomes the inferred type of a definition.
   */
  def widenTermRef(tpe: ScType): ScType = widen(tpe, stopAtLiteralType = true)

  /**
   * Repeatedly replaces a singleton type by its underlying type, which corresponds to `Type.underlying`
   * of a `SingletonType` in the Scala 3 compiler.
   *
   * Note that `object`s (and thus also `this`) are ''not'' dereferenced: their singleton type is the
   * only way to denote them, so both compilers keep it.
   */
  private def widen(tpe: ScType, stopAtLiteralType: Boolean): ScType = tpe match {
    case _: ScLiteralType if stopAtLiteralType => tpe
    case lit: ScLiteralType                    => lit.wideType
    // Singleton enum cases are `object`s, but unlike ordinary objects they have a proper
    // underlying type, which both compilers widen to, SCL-21726
    case ScProjectionType(_, enumCase: ScEnumSingletonCase) =>
      val superTypes = enumCase.superTypes
      superTypes.headOption.getOrElse(
        ScCompoundType(superTypes)(tpe.projectContext)
      )
    case designator: DesignatorOwner if designator.isSingleton =>
      // Dereferencing an `x.type` asks for the type of `x`, which may itself be inferred and lead
      // back to `x.type`, e.g. for the (illegal) `val x = x`. The guard has to be held for the whole
      // walk, because the cycle may run through the inference of an enclosing definition rather than
      // through the dereferencing below.
      dereferencing.withoutRevisiting(designator.element, ifAlreadyVisited = tpe) {
        designator.designatorSingletonType match {
          case Some(underlying) => widen(underlying, stopAtLiteralType)
          case None             => tpe
        }
      }
    case _ => tpe
  }

  /** The elements whose `x.type` is currently being dereferenced on this thread. */
  private object dereferencing {
    private val elements: ThreadLocal[Set[PsiNamedElement]] =
      ThreadLocal.withInitial(() => Set.empty[PsiNamedElement])

    def withoutRevisiting[R](element: PsiNamedElement, ifAlreadyVisited: => R)(compute: => R): R = {
      val outer = elements.get

      if (outer.contains(element)) ifAlreadyVisited
      else {
        elements.set(outer + element)
        try compute
        finally elements.set(outer)
      }
    }
  }

  /**
   * Widens all top level singleton types, also descending into the operands of unions and
   * intersections.
   *
   * Corresponds to `Type.widenSingletons` in the Scala 3 compiler. Note that, unlike the type of a
   * definition, ''type arguments'' are never widened here: `Test[1]` stays `Test[1]`.
   */
  def widenSingletons(tpe: ScType)(implicit context: Context): ScType = tpe match {
    case ScOrType(lhs, rhs) =>
      val (l, r) = (widenSingletons(lhs), widenSingletons(rhs))
      if ((l eq lhs) && (r eq rhs)) tpe else ScOrType(l, r)
    case ScAndType(lhs, rhs) =>
      val (l, r) = (widenSingletons(lhs), widenSingletons(rhs))
      if ((l eq lhs) && (r eq rhs)) tpe else ScAndType(l, r)
    case _ => widenSingleton(tpe)
  }

  /**
   * Whether a bound asks for a singleton type and therefore suppresses widening.
   *
   * Corresponds to `Type.isSingletonBounded` in the Scala 3 compiler. Intersections are inspected
   * explicitly, because conformance to `Singleton` isn't derived from the components of an
   * intersection, so that e.g. `Int with Singleton` would not be recognized otherwise.
   */
  def isSingletonBounded(bound: ScType)(implicit context: Context): Boolean = {
    implicit val projectContext: ProjectContext = bound.projectContext

    bound.removeAliasDefinitions() match {
      case ScCompoundType(components, _, _) => components.exists(isSingletonBounded)
      case ScAndType(lhs, rhs)              => isSingletonBounded(lhs) || isSingletonBounded(rhs)
      case tpe                              => !tpe.isNothing && tpe.conforms(Singleton)
    }
  }

  /**
   * Widens an inferred type `tpe` that has to stay within `bound`.
   *
   * Corresponds to `ConstraintHandling.widenInferred` in the Scala 3 compiler: singleton types are
   * only widened if the widened type still conforms to the expected type, and not at all if the
   * expected type is bounded by `Singleton`.
   */
  def widenInferred(tpe: ScType, bound: Option[ScType])(implicit context: Context): ScType =
    if (bound.exists(isSingletonBounded)) tpe
    else {
      val widened = widenSingletons(tpe)
      if ((widened eq tpe) || bound.forall(widened.conforms(_))) widened
      else tpe
    }

  /**
   * The type a compiler infers for a definition without an explicit type annotation.
   *
   * Scala 2 (`Namers.widenIfNecessary`) only drops the constant type of a `val`, but widens `var`s
   * and `def`s completely, while Scala 3 (`Namer.inferredResultType`) always widens, except for the
   * constant type of a `final val`.
   *
   * @example {{{
   *   val c: Any = ???
   *   val x: c.type = c
   *   val y = x // Scala 2: c.type, Scala 3: Any
   * }}}
   */
  def widenInferredDefinitionType(tpe: ScType, kind: DefinitionKind)(implicit context: Context): ScType =
    if (context.isScala3) {
      widenTermRef(tpe) match {
        case lit: ScLiteralType if kind == DefinitionKind.ConstantVal => lit
        case widened                                                 => widenInferred(widened, None)
      }
    } else
      kind match {
        case DefinitionKind.Var | DefinitionKind.Def => widenSingleton(tpe)
        case DefinitionKind.ConstantVal              => tpe
        case DefinitionKind.Val                      => deconst(tpe)
      }

  /** Corresponds to `Type.deconst` in the Scala 2 compiler: drops a top level constant type. */
  private def deconst(tpe: ScType): ScType = tpe match {
    case lit: ScLiteralType => lit.wideType
    case _                  => tpe
  }
}
