val /*caret*/x = if (true) false else true
!x
if (x) true
x + 5
x match {case true => false}
/*
!(if (true) false else true)
if (if (true) false else true) true
(if (true) false else true) + 5
(if (true) false else true) match {case true => false}
*/