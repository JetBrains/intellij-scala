trait Foo[+X] // List[String] <: List[CharSequence], so the code compiles
trait TString extends Foo[String]
trait TIntList extends Foo[List[Int]]
val myType: Foo[_ >: CharSequence with List[Int]] = 1 match {
  case 1 => new TString {}
  case 2 => new TIntList {}
}
//true