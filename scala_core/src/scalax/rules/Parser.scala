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

package scalax.rules

/**
 * Rules that operate on sequential input
 */
trait Parsers[T] extends RulesWithState {
  
  type X
  type Parser[+A] = Rule[A, X]
  
  /** Succeeds with the first element of the input unless input is empty. */
  def item : Parser[T]

  implicit def elem(t : T) = item.filter(_ == t)
  implicit def inElem(t : T) = inRule(elem(t))

  def readSeq[C <% Seq[T]](seq : C) = allOf(seq map elem) -^ seq

  def choice[C <% Seq[T]](seq : C) = oneOf(seq map elem)

  /** Allows rules like 'a' to 'z' */
  // the situation after Scala ticket #970 ('a' to 'z' returns RandomAccessSeq.Projection)
  implicit def iterableToChoice[TS <: Iterable[T]](iterable : TS) : Parser[T] = choice(iterable.toList)
  implicit def iterableToChoiceSeq[TS <: Iterable[T]](iterable : TS) = seqRule(iterableToChoice(iterable))
  
  // the situation before Scala ticket #970 ('a' to 'z' returns Iterator)
  // TODO remove these some day! (some time after release of Scala 2.7.2)
  implicit def iteratorToChoice[TS <: Iterator[T]](iterator : TS) : Parser[T] = choice(iterator.toList)
  implicit def iteratorToChoiceSeq[TS <: Iterator[T]](iterator : TS) = seqRule(iteratorToChoice(iterator))
}

/**
 * Rules that operate on a sequence of characters.
 */
trait Scanners extends Parsers[Char] {
  implicit def readString(string : String) : Parser[String] = readSeq(string)
  implicit def readStringIn(string : String) : InRule[S, S, String, X] = inRule(readSeq(string))

  def toString(seq : Seq[Any]) = seq.mkString("")
        
  def whitespace = item filter Character.isWhitespace *
  def newline = "\r\n" | "\n" | "\r"

  def trim[A](rule : Parser[A]) = whitespace -~ rule ~- whitespace
}


trait StringScanners extends Scanners {
  type S = String
  type X = Nothing
    
  val item = from[String] { 
    case "" => Failure
    case s => Success(s.substring(1), s.charAt(0)) 
  }
}
