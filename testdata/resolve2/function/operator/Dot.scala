object O {
  def +(p: Int) = {}
}

println(O./* applicable: false */ + 1)