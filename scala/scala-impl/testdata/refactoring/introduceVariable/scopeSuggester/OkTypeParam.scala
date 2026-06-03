class OkTypeParam[T] {

  abstract class Inner[P] {
    val m: /*begin*/Map[P, T]/*end*/
  }

}