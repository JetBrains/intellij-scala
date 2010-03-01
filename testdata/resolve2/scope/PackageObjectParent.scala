package object level1 {
	class C
	object O
	trait T
	type A = Int
	def f {}
	val v1: Int = 1
	var v2: Int = 2
}

package level1 {
  package level2 {
  class C {
    println(classOf[/* line: 2 */C])
    println(/* line: 3 */O.getClass)
    println(classOf[/* line: 4 */T])
    println(classOf[/* line: 5 */A])
    println(/* line: 6 */f)
    println(/* line: 7 */v1)
    println(/* line: 8 */v2)
  }
  }
}
