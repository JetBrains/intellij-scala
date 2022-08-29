def /*caret*/x(a: Int): Boolean = if (a == 5) false else true
!x(5)
if (x(6)) true
x(7) || x(8)
x(9) match {
  case true => false
}
/*
!(if (5 == 5) false else true)
if (if (6 == 5) false else true) true
(if (7 == 5) false else true) || (if (8 == 5) false else true)
(if (9 == 5) false else true) match {
  case true => false
}*/