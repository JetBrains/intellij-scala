//> expected.error cannot.inline.recursive.function
def /*caret*/bar(): Int = {
  println("1")
  bar()
}
bar()