implement L
class ImplementTypeAlias extends b {
  <caret>
}
abstract class b {
  type L
}<end>
class ImplementTypeAlias extends b {
  type L = this.type
}
abstract class b {
  type L
}