object InInheritors {

  class A {
    def fooa = 45

    val fos = "sdf"
  }

  class B extends A {
    def fob = 45

    val fol = new File("sdff")
  }

  class C extends B {
    def foo = 45

    val fok = new Exception
  }

  (new C).fo<caret>
}