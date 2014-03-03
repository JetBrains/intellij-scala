object Sample {
  class Outer extends A {
    trait Z {
      def goo {
        "stop here"
      }
    }

    def goo {
      new Z {}.goo
    }
  }
  def main(args: Array[String]) {
    new Outer().goo
  }
}