//> using options -Ycheck:all

val tuple: (42, true) = (42, true)
val b = tuple match
  case (1, y) => ()
  case (x, y) => ()
