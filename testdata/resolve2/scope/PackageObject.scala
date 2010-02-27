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
class C1 {
	println(classOf[/* line: 2 */C])
	println(/* line: 3 */O.getClass)
	println(classOf[/* line: 4 */T])
	println(classOf[/* line: 5 */A])
	println(/* line: 6 */f)
	println(/* line: 7 */v1)
	println(/* line: 8 */v2)
}
}

class C2 {
  println(classOf[/* line: 1 */holder./* line: 2 */C])
  println(/* line: 1 */holder./* line: 3 */O.getClass)
  println(classOf[/* line: 1 */holder./* line: 4 */T])
  println(classOf[/* line: 1 */holder./* line: 5 */A])
  println(/* line: 1 */holder./* line: 6 */f)
  println(/* line: 1 */holder./* line: 7 */v1)
  println(/* line: 1 */holder./* line: 8 */v2)
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