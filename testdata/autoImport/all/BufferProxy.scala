import collection.mutable.ArrayBuffer

val x = new ArrayBuffer[Int]
val y = new /*ref*/CloneableCollection
/*
import collection.mutable.{CloneableCollection, ArrayBuffer}

val x = new ArrayBuffer[Int]
val y = new /*ref*/CloneableCollection
*/