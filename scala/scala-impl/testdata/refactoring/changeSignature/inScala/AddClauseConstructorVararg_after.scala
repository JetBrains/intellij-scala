class AddClauseConstructorVararg(b: Boolean)(x: Int, i: Int*) {
  new AddClauseConstructorVararg(true)(10)
  new AddClauseConstructorVararg(true)(10, 0, 1, 2)
  new AddClauseConstructorVararg(b = true)(10)
  new AddClauseConstructorVararg(b = true)(10, 0, 1)
}
