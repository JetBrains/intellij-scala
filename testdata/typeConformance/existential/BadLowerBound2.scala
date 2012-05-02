//error: Foo[String] vs Foo[CharSequence] - won't compile since invariant
trait Foo[X]
trait TString extends Foo[String]
trait TIntList extends Foo[List[Int]]
val myType: Foo[_ >: CharSequence with List[Int]] = 1 match {
  case 1 => new TString {}
  case 2 => new TIntList {}
}
//false