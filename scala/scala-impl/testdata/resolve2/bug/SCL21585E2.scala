trait Father[A] {
  type B = A
  trait Son {
    def set(x: B): Unit
  }
}

trait Charles extends Father[Int] {
  trait William extends Father[String] with Son {
    def t1() = this./* resolved: true */set(1)
  }
}
