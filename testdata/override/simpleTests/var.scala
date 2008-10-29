implement too
package test

class Var extends b {
  <caret>
}
abstract class b {
  var too: b
}<end>
package test

class Var extends b {
    var too: b = _
}
abstract class b {
    var too: b
}