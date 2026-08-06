class <caret>AddClauseConstructorVararg(b: Boolean, i: Int*) {
  new AddClauseConstructorVararg(true)
  new AddClauseConstructorVararg(true, 0, 1, 2)
  new AddClauseConstructorVararg(b = true)
  new AddClauseConstructorVararg(b = true, 0, 1)
}
