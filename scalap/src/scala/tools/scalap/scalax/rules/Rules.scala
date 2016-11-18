// -----------------------------------------------------------------------------
//
//  Scalax - The Scala Community Library
//  Copyright (c) 2005-8 The Scalax Project. All rights reserved.
//
//  The primary distribution site is http://scalax.scalaforge.org/
//
//  This software is released under the terms of the Revised BSD License.
//  There is NO WARRANTY.  See the file LICENSE for the full text.
//
// -----------------------------------------------------------------------------

package scala.tools.scalap
package scalax
package rules

import scala.collection.immutable.Nil
import scala.language.postfixOps
import scala.language.implicitConversions

trait Name {
  def name : String
  override def toString: String = name
}

/** A factory for rules.
  * 
  * @author Andrew Foggin
  *
  * Inspired by the Scala parser combinator.
  */
trait Rules {
  implicit def rule[In, Out, A, X](f : In => Result[Out, A, X]) : Rule[In, Out, A, X] = new DefaultRule(f)

  implicit def inRule[In, Out, A, X](rule : Rule[In, Out, A, X]) : InRule[In, Out, A, X] = new InRule(rule)
  implicit def seqRule[In, A, X](rule : Rule[In, In, A, X]) : SeqRule[In, A, X] = new SeqRule(rule)
  
  def from[In] = new {
    def apply[Out, A, X](f : In => Result[Out, A, X]): Rule[In, Out, A, X] = rule(f)
  }
  
  def state[s] = new StateRules {
    type S = s
    val factory = Rules.this
  }
  
  def success[Out, A](out : Out, a : A): Rule[Any, Out, A, Nothing] = rule { _: Any => Success(out, a) }
  
  def failure: Rule[Any, Nothing, Nothing, Nothing] = rule { _: Any => Failure }
  
  def error[In]: Rule[In, Nothing, Nothing, In] = rule { in : In => Error(in) }
  def error[X](err : X): Rule[Any, Nothing, Nothing, X] = rule { _: Any => Error(err) }
      
  def oneOf[In, Out, A, X](rules : Rule[In, Out, A, X] *) : Rule[In, Out, A, X] = new Choice[In, Out, A, X] {
    val factory = Rules.this
    val choices = rules.toList
  }
    
  def ruleWithName[In, Out, A, X](_name : String, f : In => Result[Out, A, X]) : Rule[In, Out, A, X] with Name = 
    new DefaultRule(f) with Name {
      val name = _name
    }

  class DefaultRule[In, Out, A, X](f : In => Result[Out, A, X]) extends Rule[In, Out, A, X] {
    val factory = Rules.this
    def apply(in : In): Result[Out, A, X] = f(in)
  }
  
 /** Converts a rule into a function that throws an Exception on failure. */
  def expect[In, Out, A, Any](rule : Rule[In, Out, A, Any]) : In => A = (in) => rule(in) match {
    case Success(_, a) => a
    case Failure => throw new ScalaSigParserError("Unexpected failure")
    case Error(x) => throw new ScalaSigParserError("Unexpected error: " + x)
  }
}

/** A factory for rules that apply to a particular context.
  *
  * @requires S the context to which rules apply.
  *
  * @author Andrew Foggin
  *
  * Inspired by the Scala parser combinator.
  */
trait StateRules {
  type S
  type Rule2[+A, +X] = rules.Rule[S, S, A, X]

  val factory : Rules
  import factory._

  def apply[A, X](f : S => Result[S, A, X]): Rule[S, S, A, X] = rule(f)

  def unit[A](a : => A): Rule[S, S, A, Nothing] = apply { s => Success(s, a) }
  def read[A](f : S => A): Rule[S, S, A, Nothing] = apply { s => Success(s, f(s)) }

  def get: Rule[S, S, S, Nothing] = apply { s => Success(s, s) }
  def set(s : => S): Rule[S, S, S, Nothing] = apply { oldS => Success(s, oldS) }

  def update(f : S => S): Rule[S, S, S, Nothing] = apply { s => Success(s, f(s)) }

  def nil: Rule[S, S, Nil.type, Nothing] = unit(Nil)
  def none: Rule[S, S, None.type, Nothing] = unit(None)

  /** Create a rule that identities if f(in) is true. */
  def cond(f : S => Boolean): Rule[S, S, S, Nothing] = get filter f

  /** Create a rule that succeeds if all of the given rules succeed.
      @param rules the rules to apply in sequence.
  */
  def allOf[A, X](rules : Seq[Rule2[A, X]]): (S) => Result[S, List[A], X] = {
    def rep(in : S, rules : List[Rule2[A, X]], results : List[A]) : Result[S, List[A], X] = {
      rules match {
        case Nil => Success(in, results.reverse)
        case rule::tl => rule(in) match {
          case Failure => Failure
          case Error(x) => Error(x)
          case Success(out, v) => rep(out, tl, v::results)
        }
      }
    }
    in : S => rep(in, rules.toList, Nil)
  }


  /** Create a rule that succeeds with a list of all the provided rules that succeed.
      @param rules the rules to apply in sequence.
  */
  def anyOf[A, X](rules : Seq[Rule2[A, X]]): Rule[S, S, List[A], X] = allOf(rules.map(_ ?)) ^^ { opts => opts.flatMap(x => x) }

  /** Repeatedly apply a rule from initial value until finished condition is met. */
  def repeatUntil[T, X](rule : Rule2[T => T, X])(finished : T => Boolean)(initial : T): Rule[S, S, T, X] = apply {
    // more compact using HoF but written this way so it's tail-recursive
    def rep(in : S, t : T) : Result[S, T, X] = {
      if (finished(t)) Success(in, t) 
      else rule(in) match {
        case Success(out, f) => rep(out, f(t))
        case Failure => Failure
        case Error(x) => Error(x)
      }
    }
    in => rep(in, initial)
  }
  

}

trait RulesWithState extends Rules with StateRules {
  val factory = this
}
