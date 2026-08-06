import scala.collection.mutable.ArrayBuffer

val x = new ArrayBuffer[Int]
val y = new /*ref*/ImmutableMapAdaptor
/*
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

val x = new ArrayBuffer[Int]
val y = new mutable.ImmutableMapAdaptor
*/