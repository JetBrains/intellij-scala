trait Father[A] {
  type B = A

  trait Son {
    def set(x: B): Unit
  }
}

trait Phillip extends Father[Boolean] {
  trait Charles extends Father[Int] with Son {
    def t0() = this./* resolved: true */set(true)

    trait William extends Father[String] with Son {
      def t1() = this./* resolved: true */set(1)

      trait George extends Son {
        def t2() = this./* resolved: true */set("y")
      }
    }
  }
}
