class C {
  def <>(n: Int): C = new C
  def ><(n: Int): C = new C
  def !!(n: Int): C = new C
}

var v = new C
v /* line: 2, name: <> */ <>= 1
v /* line: 3, name: >< */ ><= 1
v /* line: 4, name: !! */ !!= 1

