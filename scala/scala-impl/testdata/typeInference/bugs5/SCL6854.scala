object SCL6854 {

  trait Foo[T] {
    def value: T
  }

  // LEVEL 1
  case class FooAny[T](value: T) extends Foo[T]

  // LEVEL 2
  case class FooNumberAny[T: Numeric](value: T) extends Foo[T]

  // Constructor
  object Foo {
    def apply[T, R <: Foo[T]](value: T)(implicit builder: Builder[T, R]): R =
      builder.buildInstance(value)

    // Builder
    trait Builder[T, R <: Foo[T]] {
      def buildInstance(value: T): R
    }

    // defining the FooAny builder, that has a lower priority
    trait Level1 {
      implicit def FooAnyBuilder[T] = new Builder[T, FooAny[T]] {
        def buildInstance(value: T) =
          FooAny(value)
      }
    }

    // removing the FooNumberAny builder also fixes the error highlighting
    object Builder extends Level1 {
      implicit def FooNumberAnyBuilder[T](implicit p: Numeric[T]) = new Builder[T, FooNumberAny[T]] {
        def buildInstance(value: T) =
          FooNumberAny(value)
      }
    }

  }

  object Main extends App {
    def log[T](name: String, ref: Foo[T], value: T): Unit =
      println(f"val $name%-12s: ${ref.getClass.getName}%-19s = $value")

    println()

    // Implicits guided type inference does not work in IntelliJ IDEA:

    val anyRef = Foo("hello, world!") // <-- ERROR HERE (View -> Type Info shows "Nothing", when it should be FooAny)
    log("anyRef", anyRef, anyRef.value)
    // <-- manifested here (syntax highlighting error)

    val anyRefExp: FooAny[String] = Foo("hello, world! (explicit)") // <-- specifying the type explicitly works
    log("anyRefExp", anyRefExp, anyRefExp.value)

    val someBoolean = Foo(true) // <-- ERROR here too
    log("someBoolean", someBoolean, someBoolean.value)
    // <-- manifested here

    val anyNumber = Foo(Long.MaxValue)
    log("anyNumber", anyNumber, anyNumber.value)

    println()

    /*start*/(anyRef, anyRefExp, someBoolean, anyNumber)/*end*/
  }

}
//(SCL6854.FooAny[String], SCL6854.FooAny[String], SCL6854.FooAny[Boolean], SCL6854.FooNumberAny[Long])