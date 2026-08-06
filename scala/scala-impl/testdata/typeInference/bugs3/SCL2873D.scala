object A
class B
implicit def a2b(a: A.type): B = null

(try A catch { case x => /*start*/A/*end*/ }): B
(try {(); A} catch { case x => (); A }): B

//B