try {

} catch /*start*/{
  case r: RuntimeException => r
  case t => t
}/*end*/
//PartialFunction[Throwable, Throwable]