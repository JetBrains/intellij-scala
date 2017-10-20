object MultiMapUtil {

  class MultiMap[K, V, M <: Map[K, Set[V]]](val map: M, emptySet: Set[V]) {
    // commented out, not important for this issue
  }

  // Warning! Here live dragons.
  implicit def mapToMultiMap[K, VV, SS[V] <: Set[V], M[K, S] <: Map[K, S]] {
    implicit def foo(s: Set[VV]) = 1
    val z: SS[VV] = exit()
    /*start*/z + 1/*end*/
  }
}
//Int