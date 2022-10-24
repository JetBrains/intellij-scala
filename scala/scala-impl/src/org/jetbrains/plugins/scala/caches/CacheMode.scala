package org.jetbrains.plugins.scala.caches

/**
 * Changes behaviour of caching
 *
 * Some cache macros support a `cacheMode` parameter of type CacheMode[R]
 * in the signature of the annotated function. If present, the macro
 * will generate special code that modifies the behaviour of the caching
 * depending on the mode given through the `cacheMode` parameter.
 *
 * {{{
 *   @cached(..., ...)
 *   def func(otherParameter: Ty, cacheMode: CacheMode[ReturnTy] = CacheMode.Default): ReturnTy = ...
 * }}}
 *
 * @tparam R Return type of the annotated function
 */
sealed trait CacheMode[+R]

object CacheMode {
  /**
   * Same as [[CacheMode.ComputeIfOutdated]]
   */
  val Default: CacheMode[Nothing] = ComputeIfOutdated

  /**
   * Computes if the cache is empty or outdated and suss never returns an outdated value.
   */
  case object ComputeIfOutdated extends CacheMode[Nothing]

  /**
   * Computes if the cache is empty. If the cache contains an outdated value, that value is returned.
   */
  case object ComputeIfNotCached extends CacheMode[Nothing]

  /**
   * Never computes. If the cache is empty, the given default is returned.
   * If the cache contains an outdated value, that value is returned.
   *
   * @param default the value that is returned if the cache does not contain any value.
   * @tparam R Return type of the annotated function
   */
  case class CachedOrDefault[R](default: R) extends CacheMode[R]
}
