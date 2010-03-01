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

class C2 {
  println(classOf[/* line: 1 */holder./* line: 2 */C])
  println(/* line: 1 */holder./* line: 3 */O.getClass)
  println(classOf[/* line: 1 */holder./* line: 4 */T])
  println(classOf[/* line: 1 */holder./* line: 5 */A])
  println(/* line: 1 */holder./* line: 6 */f)
  println(/* line: 1 */holder./* line: 7 */v1)
  println(/* line: 1 */holder./* line: 8 */v2)
}
