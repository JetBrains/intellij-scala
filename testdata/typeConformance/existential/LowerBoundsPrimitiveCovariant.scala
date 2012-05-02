trait Foo[+X] //covariant lower bound compiles if the right side type has or is a supertype of the left side type (here: Long <: AnyVal) 
trait TInt extends Foo[Int]
trait TDouble extends Foo[Double]
trait TLong extends Foo[Long]

val myType: Foo[_ >: Long] = 1 match { 
  case 1 => null.asInstanceOf[TInt]  
  case 2 => null.asInstanceOf[TLong]  
  case 3 => null.asInstanceOf[TDouble] 
}
//true