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

trait IncrementalParsers[A] extends Parsers[A] with MemoisableRules {
  type S = IncrementalInput[A]
  val item = from[S] { _ next }
}

trait IncrementalScanners extends IncrementalParsers[Char] with Scanners

class IncrementalInput[A]
    extends Input[A] 
    with DefaultMemoisable 
    with Ordered[IncrementalInput[A]] { self : IncrementalInput[A] =>

  var next : Result[IncrementalInput[A], A, Nothing] = Failure
  var index : Int = 0

  def compare(other : IncrementalInput[A]) = index - other.index

  /**
   * Specifies a change to the document.
   *
   * @param pos number of elements before the change
   * @param deleted number of elements deleted
   * @param inserted sequence of values to insert
   */
  def edit(pos : Int, deleted: Int, inserted : Seq[A]) {
    edit(0, pos, deleted, inserted.elements)
  }

  /** Tail-recursive function.  Will only work from Scala 2.6.1. */
  private def edit(index: Int, pos : Int, deleted: Int, values : Iterator[A]) {
    this.index = index
    if (index <= pos) cleanResults(pos)
    if (index == pos) deleteElements(deleted)
    if (index >= pos && values.hasNext) insert(values.next)

    // recursive call to next element
    if (hasNextElement) nextElement.edit(index + 1, pos, deleted, values)
  }

  /** Delete all Failure results up to pos
   *  and all Success results up to pos that point beyond pos
   */
  protected def cleanResults(pos : Int) = map.retain { 
    case (_, Success(elem : IncrementalInput[_], _)) if elem.index < pos => true 
    case _ => false 
  }

  /** Delete elements */
  protected def deleteElements(count : Int) = for (_ <- 1 to count) delete
  
  /** Delete current element value */
  protected def delete() = if (hasNextElement) next = nextElement.next

  /** Insert an element */
  protected def insert(value : A) {
    val elem = newElement
    elem.next = next
    next = Success(elem, value)
  }
  
  protected def newElement = new IncrementalInput[A]
  
  protected def hasNextElement = next match {
    case Success(_, _) => true
    case _ => false
  }
  
  protected def nextElement = next match {
    case Success(element, _) => element
    case _ => throw new RuntimeException("No next element")
  }
  
  override def toString = "@" + index
}
