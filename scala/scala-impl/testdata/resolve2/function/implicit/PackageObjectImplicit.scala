package object holder {
  implicit val v: Int = 1
}

package holder {
	class C {
  	def f(implicit i: Int) = {}

  	println(/* line: 7 */ f)
	}
}


