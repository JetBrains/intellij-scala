object O {
  def unary_+ = {}
  def unary_- = {}
  def unary_! = {}
  def unary_~ = {}

  def unary_* = {}
}

println(/* line: 2, name: unary_+  */+O)
println(O./* line: 2 */unary_+)

println(/* line: 3, name: unary_- */-O)
println(O./* line: 3 */unary_-)

println(/* line: 4, name: unary_! */!O)
println(O./* line: 4 */unary_!)

println(/* line: 5, name: unary_~ */~O)
println(O./* line: 5 */unary_~)

println(/* resolved: false */*O)
println(O./* line: 7 */unary_*)

