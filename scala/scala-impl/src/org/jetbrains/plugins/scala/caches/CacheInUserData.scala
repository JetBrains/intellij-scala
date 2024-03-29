package org.jetbrains.plugins.scala.caches

import org.jetbrains.plugins.scala.caches.stats.Tracer
import org.jetbrains.plugins.scala.util.UIFreezingGuard

object CacheInUserData {
  // TODO cacheInUserData0[R] == cacheInUserDataN[Unit, R]
  // Code generated by @CachedInUserData macro annotation, method without parameters
  def cacheInUserData0[E: ProjectUserDataHolder, R](id: String, name: String, dataHolder: E, dependency: => AnyRef, f: => R): R = {
    val tracer = Tracer(id, name)
    tracer.invocation()
    val key = CachesUtil.getOrCreateKey[CachesUtil.CachedRef[R]](id)
    val holder = CachesUtil.getOrCreateCachedRef[E, R](dataHolder, key, id, name, () => dependency) // TODO (psiElement)
    val fromCachedHolder = holder.get()
    if (fromCachedHolder != null) {
      return fromCachedHolder
    }
    val stackStamp = RecursionManager.markStack()
    tracer.calculationStart()
    val result = try {
      val guardedResult = if (UIFreezingGuard.isAlreadyGuarded) {
        f
      } else {
        UIFreezingGuard.withResponsibleUI {
          f
        }
      }
      guardedResult
    } finally {
      tracer.calculationEnd()
    }
    if (stackStamp.mayCacheNow()) {
      val race = {
        holder.compareAndSet(null.asInstanceOf[R], result);
        holder.get()
      }
      if (race != null) race else result
    } else {
      result
    }
  }

  // Code generated by @CachedInUserData macro annotation, method with parameters
  def cacheInUserDataN[E: ProjectUserDataHolder, T <: Product, R](id: String, name: String, dataHolder: E, dependency: => AnyRef, v: T, f: => R): R = {
    val tracer = Tracer(id, name)
    tracer.invocation()
    val key = CachesUtil.getOrCreateKey[CachesUtil.CachedMap[T, R]](id)
    val holder = CachesUtil.getOrCreateCachedMap[E, T, R](dataHolder, key, id, name, () => dependency) // TODO (psiElement)
    val fromCachedHolder = holder.get(v)
    if (fromCachedHolder != null) {
      return fromCachedHolder
    }
    val stackStamp = RecursionManager.markStack()
    tracer.calculationStart()
    val result = try {
      val guardedResult = if (UIFreezingGuard.isAlreadyGuarded) {
        f
      } else {
        UIFreezingGuard.withResponsibleUI {
          f
        }
      }
      guardedResult
    } finally {
      tracer.calculationEnd()
    }
    if (stackStamp.mayCacheNow()) {
      val race = holder.putIfAbsent(v, result)
      if (race != null) race else result
    } else {
      result
    }
  }

/*
  @CachedInUserData(dataHolder, ModTracker.anyScalaPsiChange)
  def foo(): String = "Foo"

  def foo(): String = {
    def org$example$Example$foo$cachedFun(): String = {
      val __guardedResult__: String = if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded)
        "Foo"
      else
        _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI("Foo");
      __guardedResult__
    };
    val org$example$Example$foo$$tracer = _root_.org.jetbrains.plugins.scala.caches.stats.Tracer("org$example$Example$foo$cacheKey", "Example.foo");
    org$example$Example$foo$$tracer.invocation();
    val org$example$Example$foo$data = ();
    val org$example$Example$foo$key = _root_.org.jetbrains.plugins.scala.caches.CachesUtil.getOrCreateKey[_root_.org.jetbrains.plugins.scala.caches.CachesUtil.CachedRef[String]]("org$example$Example$foo$cacheKey");
    val org$example$Example$foo$element = dataHolder;
    val org$example$Example$foo$holder = _root_.org.jetbrains.plugins.scala.caches.CachesUtil.getOrCreateCachedRef[org$example$Example$foo$element.type, String](org$example$Example$foo$element, org$example$Example$foo$key, "org$example$Example$foo$cacheKey", "Example.foo", (() => ModTracker.anyScalaPsiChange));
    val fromCachedHolder = org$example$Example$foo$holder.get();
    if (fromCachedHolder.$bang$eq(null))
      return fromCachedHolder
    else
      ();
    val stackStamp = org.jetbrains.plugins.scala.caches.RecursionManager.markStack();
    org$example$Example$foo$$tracer.calculationStart();
    val org$example$Example$foo$result = try {
      val __guardedResult__: String = if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded)
        "Foo"
      else
        _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI("Foo");
      __guardedResult__
    } finally org$example$Example$foo$$tracer.calculationEnd();
    if (stackStamp.mayCacheNow())
      {
        val race = {
          org$example$Example$foo$holder.compareAndSet(null, org$example$Example$foo$result);
          org$example$Example$foo$holder.get()
        };
        if (race.$bang$eq(null))
          race
        else
          org$example$Example$foo$result
      }
    else
      org$example$Example$foo$result
  }
*/

/*
  @CachedInUserData(dataHolder, ModTracker.anyScalaPsiChange)
  def bar(x: Int): String = "Foo"

  def foo(x: Int): String = {
    def org$example$Example$foo$cachedFun(): String = {
      val __guardedResult__: String = if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded)
        "Foo"
      else
        _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI("Foo");
      __guardedResult__
    };
    val org$example$Example$foo$$tracer = _root_.org.jetbrains.plugins.scala.caches.stats.Tracer("org$example$Example$foo$cacheKey", "Example.foo");
    org$example$Example$foo$$tracer.invocation();
    val org$example$Example$foo$data = x;
    val org$example$Example$foo$key = _root_.org.jetbrains.plugins.scala.caches.CachesUtil.getOrCreateKey[_root_.org.jetbrains.plugins.scala.caches.CachesUtil.CachedMap[Int, String]]("org$example$Example$foo$cacheKey");
    val org$example$Example$foo$element = dataHolder;
    val org$example$Example$foo$holder = _root_.org.jetbrains.plugins.scala.caches.CachesUtil.getOrCreateCachedMap[org$example$Example$foo$element.type, Int, String](org$example$Example$foo$element, org$example$Example$foo$key, "org$example$Example$foo$cacheKey", "Example.foo", (() => ModTracker.anyScalaPsiChange));
    val fromCachedHolder = org$example$Example$foo$holder.get(org$example$Example$foo$data);
    if (fromCachedHolder.$bang$eq(null))
      return fromCachedHolder
    else
      ();
    val stackStamp = org.jetbrains.plugins.scala.caches.RecursionManager.markStack();
    org$example$Example$foo$$tracer.calculationStart();
    val org$example$Example$foo$result = try {
      val __guardedResult__: String = if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded)
        "Foo"
      else
        _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI("Foo");
      __guardedResult__
    } finally org$example$Example$foo$$tracer.calculationEnd();
    if (stackStamp.mayCacheNow())
      {
        val race = org$example$Example$foo$holder.putIfAbsent(org$example$Example$foo$data, org$example$Example$foo$result);
        if (race.$bang$eq(null))
          race
        else
          org$example$Example$foo$result
      }
    else
      org$example$Example$foo$result
  }
*/
}
