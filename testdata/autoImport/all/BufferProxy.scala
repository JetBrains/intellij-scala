import scala.collection.mutable.ArrayBuffer

val x = new ArrayBuffer[Int]
val y = new /*ref*/ImmutableMapAdaptor
/*
import scala.collection.mutable.{ArrayBuffer, ImmutableMapAdaptor}

val x = new ArrayBuffer[Int]
val y = new ImmutableMapAdaptor
*/