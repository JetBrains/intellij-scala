class RemoveClauseConstructor(i: Int = 1)(b: Boolean) {
  new <caret>RemoveClauseConstructor(1)(true)
  new RemoveClauseConstructor()(true)
}
