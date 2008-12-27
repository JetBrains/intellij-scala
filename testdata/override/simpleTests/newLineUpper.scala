implement too
package test

class UpperNewLine extends b {
  <caret>
  def foo(): Int = 3
}
abstract class b {
  def too: b
}<end>
package test

class UpperNewLine extends b {
  def too: b = null

  def foo(): Int = 3
}
abstract class b {
  def too: b
}