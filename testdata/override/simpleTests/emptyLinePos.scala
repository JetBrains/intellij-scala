implement too
package test
class Empty extends b {
  def foo(): Int = 3


  <caret>


}
abstract class b {
  def too: b
}<end>
package test

class Empty extends b {
  def foo(): Int = 3


  def too: b = null
}
abstract class b {
  def too: b
}