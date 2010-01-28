implement too
package test

class Val extends b {
  <caret>
}
abstract class b {
  val too: b
}<end>
package test

class Val extends b {
  val too: b = _
}
abstract class b {
  val too: b
}