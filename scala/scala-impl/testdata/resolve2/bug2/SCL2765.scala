trait M[X] extends (X => Int) {
  def apply(x: X) = 0
}

def i(i: String) = i
def i(i: Int) = i

def foo[A: M](a: A) = {
/*resolved: true, line: 6*/i(a)
}