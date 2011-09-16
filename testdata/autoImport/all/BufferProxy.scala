import collection.mutable.ArrayBuffer

val x = new ArrayBuffer[Int]
val y = new /*ref*/ImmutableMapAdaptor
/*
import collection.mutable.{ImmutableMapAdaptor, ArrayBuffer}

val x = new ArrayBuffer[Int]
val y = new /*ref*/ImmutableMapAdaptor
*/