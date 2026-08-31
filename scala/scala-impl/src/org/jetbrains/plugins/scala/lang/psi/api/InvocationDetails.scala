package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.{PsiElement, PsiParameterList, PsiTypeParameterList}
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails.ArgumentClause
import org.jetbrains.plugins.scala.lang.psi.api.base.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.InvocationDetailsImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * InvocationDetails is the central interface to get information about invocations within some syntax.
 *
 * In Scala, it is often very challenging to see from the syntax how calls are structured.
 * Take, for instance:
 *   f()[Int](using 3)[Float]
 *
 * Where are the invocations? Is it all one method-call? Or is one part returning a function that is then called?
 * Or is one part returning a Type with an apply method? Do we face an eta-expansion?
 * What using/implicit clauses are involved? What type argument clauses are omitted because they are inferred?
 *
 * This interface answers all these questions.
 *
 * You can acquire InvocationDetails from an [[InvocationDetailsOwner]]. These are syntax elements that can potentially
 * represent an invocation but may not. One instance of InvocationDetails will always be associated with exactly one
 * [[InvocationDetailsOwner]]. For `def f() = 0`, only `f()` will have InvocationDetails while `f` will not.
 * For `def f = () => 0`, both will actually have separate InvocationDetails.
 * Invocations of constructors are also represented by InvocationDetails.
 *
 * InvocationDetails always point to exactly one target. Subsequent invocations to further targets are represented
 * by separate InvocationDetails.
 * For every (type)parameter clause of the target [[argumentClauses]] will have one entry.
 *  - for explicitly written type parameter clauses: [[InvocationDetails.TypeClause]]
 *  - the same for omitted type parameter clauses but type parameters will be inferred and [[InvocationDetails.TypeClause.origin]] will be `None`
 *  - for explicitly provided (implicit/using) value parameter clauses: [[InvocationDetails.ValueClause]]
 *  - for omitted implicit/using value parameter clauses: [[InvocationDetails.ImplicitValueClause]]
 *  - for omitted non-implicit/using value parameter clauses, eta-expansion happens: [[InvocationDetails.EtaExpandedClause]]
 *  - for omitted empty clauses in constructor invocations or targeting java methods with no parameters: [[InvocationDetails.AutoAppliedClause]]
 *
 * Some invocations might actually not be invocations, but partially applied or eta-expanded functions.
 * For these cases we still generate InvocationDetails on the appropriate syntax element.
 * In that case [[isPartiallyApplied]] will be `true` and [[argumentClauses]] will have at least one clause with
 * [[ArgumentClause.Value.isEtaExpanded]] being `ture` or a value clause with an argument pointing to an underscore section.
 *
 * Here are some examples and why they would yield [[InvocationDetails]] or not:
 *
 * ```scala
 * // the following examples yield InvocationDetails if ...
 * f       // ... f is a parameterless method (with or without type parameters)
 * f[Int]  // ... f with type parameters or f yields a type that has an apply method with type parameters
 *         //     (omitted argument clauses may be eta-expanded)
 * f _     // ... f has a function type
 * f()     // ... f is a method, a function, a class (universal apply), a value with apply-method
 *         //     (omitted argument clauses may be eta-expanded)
 * 1 + 1   // + is a method on Int
 * a.b = 1 // a.b_=(i: Int) is defined
 * new A   // A has a constructor
 * ```
 *
 * @syntax markdown
 */
trait InvocationDetails {
  def origin: InvocationDetailsOwner

  /** The expression the call is applied to, `a` in `a.f(b)`. */
  def thisExpr: Option[ScExpression]

  /** What the call resolves to, [[None]] if it does not resolve. */
  def target: Option[ScalaResolveResult]

  /** The clauses the call applies its arguments to, in the order of the signature of the callee. */
  def argumentClauses: Seq[ArgumentClause]

  /**
   * Whether the invocation targets an `apply` method which is not explicitly named.
   * This is the case for values with apply-methods, including functions.
   * Despite the naming, this method returns `false` for universal applies.
   */
  def isApply: Boolean

  /**
   * Whether the invocation targets a constructor but omits the `new` keyword (Scala 3 feature: Universal Apply).
   *
   * A `case class` is applied to its arguments by the `apply` of its companion, which the compiler
   * writes out for it, so `A(1)` of one is an [[isApply]] and no universal apply.
   */
  def isUniversalApply: Boolean

  /**
   * Weather this invocation targets a constructor. This is also true for universal applies.
   */
  def isConstructorInvocation: Boolean

  /**
   * Whether the invocation targets an `update` method which is not explicitly named.
   */
  def isUpdate: Boolean

  /**
   * Whether the invocation targets a setter which is not explicitly named, `a.x = 1` calling
   * `a.x_=(1)` where `def x_=(i: Int)` is defined. The right side of the assignment is the argument
   * of the one clause the setter takes.
   *
   * An assignment to a `var`, to a class parameter or to a Java field writes it rather than calling
   * anything, so it is no invocation at all.
   */
  def isAssignmentCall: Boolean

  /**
   * Whether the call evaluates to a function that completes it rather than to the result of the
   * callee, which it does in three cases:
   * 1. it leaves a value clause of the callee unapplied, as `f` and `f(1)` of
   *    `def f(i: Int)(j: Int)` do,
   * 2. the source turns it into a function with a trailing underscore like `f _`,
   * 3. or it applies a clause to placeholders, `f(_)` and `f(1, _)`.
   *
   * Note that `f(1)(2)` is none of the three.
   *
   * Only the first two are an eta expansion in the sense of the specification, the third one is
   * placeholder syntax for an anonymous function, so the three go by the looser name they share.
   *
   * The clauses such a call does apply are reported as they are: its type arguments are inferred as
   * ever, and a using clause it applies, as the leading one of `def f(using Int)(i: Int)` does, is
   * reported as any other implicit clause. The clauses it leaves unapplied are reported as
   * [[EtaExpandedClause]], since they are applied to the arguments of the function it evaluates to.
   */
  def isPartiallyApplied: Boolean
}

object InvocationDetails {
  def of(owner: ConstructorInvocationLike): InvocationDetails = InvocationDetailsImpl.of(owner)
  def of(owner: InvocationDetailsOwner): Option[InvocationDetails] = InvocationDetailsImpl.of(owner)

  /**
   * One type argument of a type clause: the type parameter it instantiates, the type it instantiates
   * it with, and the type element it is written as, if the source writes it out.
   */
  final case class TypeArgument(
    parameter: TypeParameter,
    tpe:       ScType,
    origin:    Option[ScTypeElement]
  ) {
    def isExplicit: Boolean = origin.isDefined
  }

  /**
   * One clause of arguments applied to one clause of the signature of the callee.
   *
   * A clause is explicit if the source writes its arguments out. The type arguments of an explicit
   * clause may still be partly inferred, as with the named type arguments of `f[A = Int]`, so
   * explicitness is tracked per argument as well, see [[TypeArgument.isExplicit]].
   */
  sealed trait ArgumentClause {

    /** The clause of the signature of the callee the arguments are applied to. */
    def target: ArgumentClauseTarget

    /** The arguments as the source writes them, [[None]] if the source leaves them out. */
    def origin: Option[PsiElement]

    /**
     * The element the arguments follow, which is the element they would be written after if they
     * were written out.
     */
    def anchor: PsiElement

    final def isExplicit: Boolean = origin.isDefined
  }

  object ArgumentClause {

    sealed trait Type extends ArgumentClause {
      override def target: ArgumentClauseTarget.Type
      override def origin: Option[ScTypeArgs]

      def arguments: Seq[TypeArgument]
    }

    sealed trait Value extends ArgumentClause {
      override def target: ArgumentClauseTarget.Value

      /** The argument list of the call, or the operand of an infix, prefix or postfix call. */
      override def origin: Option[PsiElement]
      def isEtaExpanded: Boolean
      def isAutoApplied: Boolean

      /**
       * Whether the compiler packs this clause's arguments into one tuple argument, as in
       * `f(1, 2)` for `def f(pair: (Int, Int))`. An explicitly written tuple is not auto-tupling.
       * This also includes adapting an empty argument list to a single `Unit` argument.
       */
      def isAutoTupling: Boolean
    }
  }

  /**
   * The clause of the signature of the callee that an [[ArgumentClause]] applies its arguments to.
   *
   * A Scala or Java callee has a clause of its own to point at, while a synthetic function stands for
   * its clauses itself, see [[ArgumentClauseTarget.Synthetic]].
   */
  sealed trait ArgumentClauseTarget {

    /** The element the clause is, which is the callee itself for a synthetic function. */
    def element: PsiElement
  }

  object ArgumentClauseTarget {

    /** What a clause of type arguments is applied to. */
    sealed trait Type extends ArgumentClauseTarget

    /** What a clause of value arguments is applied to. */
    sealed trait Value extends ArgumentClauseTarget

    /**
     * The type parameter clause of a callee, an
     * [[org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause]] of a Scala one,
     * both of which are a `PsiTypeParameterList`.
     */
    final case class TypeParameters(clause: PsiTypeParameterList) extends Type {
      override def element: PsiTypeParameterList = clause
    }

    /** The parameter clause of a Scala callee. */
    final case class ScalaParameters(clause: ScParameterClause) extends Value {
      override def element: ScParameterClause = clause
    }

    /** The parameter list of a Java callee. */
    final case class JavaParameters(list: PsiParameterList) extends Value {
      override def element: PsiParameterList = list
    }

    /**
     * One of the synthetic functions the compiler makes up for the primitives, `Int#+` and
     * `Any#asInstanceOf`, which stands for its own clauses since it has no PSI for them: it is a
     * light element with no AST to hold one. It has one clause of each kind at most, so the function
     * and the kind of the target identify the clause.
     */
    final case class Synthetic(function: ScSyntheticFunction) extends Type with Value {
      override def element: ScSyntheticFunction = function
    }
  }

  /** A clause of type arguments, written out or inferred. */
  final case class TypeClause(
    origin:    Option[ScTypeArgs],
    arguments: Seq[TypeArgument]
  )(
    override val target: ArgumentClauseTarget.Type,
    override val anchor: PsiElement
  ) extends ArgumentClause.Type

  /** A clause of value arguments the source writes out. */
  final case class ValueClause(
    argsElement:   PsiElement,
    arguments:     Seq[(ScExpression, Parameter)],
    isAutoTupling: Boolean
  )(
    override val target: ArgumentClauseTarget.Value,
    override val anchor: PsiElement
  ) extends ArgumentClause.Value {
    override def origin: Some[PsiElement] = Some(argsElement)
    override def isEtaExpanded: false = false
    override def isAutoApplied: false = false
  }

  /**
   * A clause of implicit arguments the implicit search found. Its `constraints` are the ones the
   * found arguments impose on the type parameters of the callee, which may be the only source of
   * inference for them, as for `A` of `def get[A](using A): A`.
   */
  final case class ImplicitValueClause(
    arguments:   Seq[ScalaResolveResult],
  )(
    override val target: ArgumentClauseTarget.Value,
    override val anchor: PsiElement,
    val constraints: ConstraintSystem
  ) extends ArgumentClause.Value {
    override def origin: None.type = None
    override def isEtaExpanded: false = false
    override def isAutoApplied: false = false
    override def isAutoTupling: false = false
  }

  /**
   * A clause the invocation leaves unapplied, and which the function it is eta expanded to takes instead:
   * `f` of `def f(i: Int)(j: Int)` leaves both clauses to the `Int => Int => Unit` it evaluates to,
   * `f(1)` leaves the second one to its `Int => Unit`.
   *
   * A using clause is eta expanded rather than filled in by the implicit search when the expected
   * type asks for a context function, as the `using Int` of `def f(i: Int)(using Int): Int` does in
   * `val g: Int => Int ?=> Int = f`.
   *
   * Its `parameters` are the ones of the clause of the callee, with the type arguments of the call
   * substituted, so the `u` of `def f[T](t: T)(u: T)` is an `Int` in `f(1)`.
   */
  final case class EtaExpandedClause(
    parameters: Seq[Parameter]
  )(
    override val target: ArgumentClauseTarget.Value,
    override val anchor: PsiElement,
  ) extends ArgumentClause.Value {
    override def origin: None.type = None
    override def isEtaExpanded: true = true
    override def isAutoApplied: false = false
    override def isAutoTupling: false = false
  }

  /**
   * An empty parameter clause scala applies automatically where the source writes no argument list
   * for it, which it does where the clause cannot be eta expanded instead:
   *  - for java methods with empty parameter lists, i.e. `obj.hashCode` -> `obj.hashCode()`
   *  - for constructors, which have to be applied at all, i.e. `new A` -> `new A()`
   */
  final case class AutoAppliedClause()(
    override val target: ArgumentClauseTarget.Value,
    override val anchor: PsiElement,
  ) extends ArgumentClause.Value {
    override def origin: None.type = None
    override def isEtaExpanded: false = false
    override def isAutoApplied: true = true
    override def isAutoTupling: false = false
  }
}
