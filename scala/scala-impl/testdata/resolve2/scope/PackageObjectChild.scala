package object holder {
	class C
	object O
	trait T
	type A = Int
	def f {}
	val v1: Int = 1
	var v2: Int = 2
}

package holder {
}

class C3 {
  println(classOf[/* resolved: false */C])
  println(/* resolved: false */O.getClass)
  println(classOf[/* resolved: false */T])
  println(classOf[/* resolved: false */A])
  println(/* resolved: false */f)
  println(/* resolved: false */v1)
  println(/* resolved: false */v2)
}