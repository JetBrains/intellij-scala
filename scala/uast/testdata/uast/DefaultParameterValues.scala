// annotation names in the `.render.txt` file should be changed to the right fqn after fix in the type system
object Test {
  def foo(@org.jetbrains.annotations.NotNull a: Int = 1, @org.jetbrains.annotations.Nullable foo: String = null) {
  }
}