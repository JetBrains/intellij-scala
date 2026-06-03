class DeprecatedOverloads {
  @deprecated def foo(i: Int): Unit = {}
  def foo(i: Int, s: String): Unit = {}
  @Deprecated def foo(s: String): Unit = {}
  @Deprecated(forRemoval = true) def foo(s: String, b: Boolean): Unit = {}
}

val y = new DeprecatedOverloads
y.foo(<caret>)
/*
TEXT: i: Int, STRIKEOUT: true
TEXT: i: Int, s: String, STRIKEOUT: false
TEXT: s: String, STRIKEOUT: true
TEXT: s: String, b: Boolean, STRIKEOUT: true
*/