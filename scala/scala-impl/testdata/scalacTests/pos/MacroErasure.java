class MacroErasure {
  def app(f: Any => Any, x: Any): Any = macro MacroErasure.appMacro
  def app[A](f: A => Any, x: Any): Any = macro MacroErasure.appMacroA[A]
}
