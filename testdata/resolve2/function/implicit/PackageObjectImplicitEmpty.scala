package object holder {
}

package holder {
	class C {
  	def f(implicit i: Int) = {}

  	println(/* line: 6, applicable: false */ f)
	}
}


