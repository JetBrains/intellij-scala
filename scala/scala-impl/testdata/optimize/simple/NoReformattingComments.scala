// Notification message: Removed 1 import
package x

import scala.collection.immutable.BitSet
import scala.collection.mutable
import scala.util.Try

class NoReformattingComments {

  /** Some creatively formatted doc comment

    *
    *
 *  @param string
 *
    **/
  def foo(string: String): Unit = {
    val s: mutable.Seq[String] = ???
    val a: BitSet = ???
  }
}
/*
package x

import scala.collection.immutable.BitSet
import scala.collection.mutable

class NoReformattingComments {

  /** Some creatively formatted doc comment

    *
    *
 *  @param string
 *
    **/
  def foo(string: String): Unit = {
    val s: mutable.Seq[String] = ???
    val a: BitSet = ???
  }
}
*/