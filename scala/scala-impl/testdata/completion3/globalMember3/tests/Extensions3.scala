package tests

object Extensions3:
  def foo =
    if (true) then
      extension (b: Boolean)
        def unreachableLocalExtension: Int = if b then 1 else 0
    else ()
