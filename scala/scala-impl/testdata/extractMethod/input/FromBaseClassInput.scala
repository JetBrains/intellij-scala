object Bug {

  class C  {
    val x = 1
  }


  class A  {
    class B extends C {
      def foo() = {
/*start*/
        x + 1
/*end*/
      }
    }
  }
}
/*
object Bug {

  class C  {
    val x = 1
  }


  class A  {
    class B extends C {
      def foo() = {

        testMethodName

      }

      def testMethodName: Int = {
        x + 1
      }
    }
  }
}
*/