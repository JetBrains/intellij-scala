object Breed extends Enumeration {
	val doberman = Value("Doberman Pinscher")
}

object Main {
	import Breed._
	def m(p: Breed.Value) {}

	m(/*start*/doberman/*end*/)		// expected: Breed.Value, actual: Enumeration.this.type#Value
	m(Breed.doberman)	// ok
}
//Breed.Value