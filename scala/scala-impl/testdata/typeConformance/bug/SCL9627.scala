class Value(val v: Int) extends AnyVal

val v3: Option[AnyVal] = Some(new Value(1)) // IntelliJ marks this as error
//True