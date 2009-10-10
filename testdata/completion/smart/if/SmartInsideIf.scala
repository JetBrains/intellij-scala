object SmartInsideIf {
  def foo: Boolean = 45

  if (if (true) fo/*caret*/) true
}
//foo