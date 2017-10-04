trait Apply {
  def apply(a: Int) = a
}
def foo(compound: Any with Apply, regular: Apply) {
  compound(<caret>0) // no param info
}
//a: Int