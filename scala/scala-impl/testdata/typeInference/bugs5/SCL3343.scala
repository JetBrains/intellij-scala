class Omg[A] {
  def wtf(qwe: A => Unit) {}
  def ewq() {
    wtf { /*start*/_ == "sumfing"/*end*/ }
  }
}
//A => Unit