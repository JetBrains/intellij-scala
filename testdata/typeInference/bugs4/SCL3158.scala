implicit object I2S extends (Int => String) {
  def apply(v1: Int): String = v1.toString()
}

def foo(x: String) = x
foo(/*start*/1/*end*/)
//String