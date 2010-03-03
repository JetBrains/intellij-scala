package object level1 {
  implicit val v: Int = 1
}

package level1 {
package level2 {
	class C {
  	def f(implicit i: Int) = {}

  	println(/* line: 8 */ f)
	}
}
}

