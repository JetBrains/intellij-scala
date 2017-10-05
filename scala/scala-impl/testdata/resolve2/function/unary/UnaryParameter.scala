object O {
  def unary_+(p: Int) {}
  def unary_+(p: String) {}
  def unary_+(a: Int, b: Int) {}
}

println((/* line: 2, name: unary_+ */+O)(1))
println(O./* line: 2 */unary_+(1))

println((/* line: 3, name: unary_+ */+O)(""))
println(O./* line: 3 */unary_+(""))

println((/* line: 4, name: unary_+ */+O)(1, 2))
println(O./* line: 4 */unary_+(1, 2))
