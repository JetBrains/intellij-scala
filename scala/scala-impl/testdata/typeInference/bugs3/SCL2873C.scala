object A
class B
implicit def a2b(a: A.type): B = null

(try /*start*/A/*end*/ catch { case x => A }): B
(try {(); A} catch { case x => (); A }): B

//B