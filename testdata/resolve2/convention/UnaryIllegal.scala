object O {
  def unary_* = {}
  def unary_+(p: Int) = {"~"}
}

println(/* resolved: false */*O)
println(O./* line: 2 */unary_*)

println(/* applicable: false */+O)
println(1/* resolved: false */+O)
println(O./* line: 3 */unary_+(1))