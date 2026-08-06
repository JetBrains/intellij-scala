package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.{CheckedDisposable, Disposer, UserDataHolderBase, UserDataHolderEx}

import java.util.concurrent.ConcurrentHashMap
import scala.annotation.nowarn

object UserDataHolderDelegator {
  private val delegates = new ConcurrentHashMap[Disposable, UserDataHolderBase]

  @nowarn("cat=deprecation")
  def userDataHolderFor(holder: Disposable): UserDataHolderEx = {
    if (Disposer.isDisposed(holder)) {
      // Return a temporary UserDataHolder
      // This is just to alleviate problems when a Disposable is being disposed
      new UserDataHolderBase
    } else {
      delegates.computeIfAbsent(holder, holder => {
        // Ideally we wouldn't need holder to be a disposable to support arbitrary objects.
        // delegates could then be a weak hashmap and the userdata gets removed out when holder is collected.
        // Unfortunately this can lead to memory leaks when values in the userdata reference the holder.
        // In that case the holder wouldn't get collected and subsequently not removed from delegates.
        // Instead we force holder to be a Disposable and relay on the deposing mechanism to remove it from the map
        //assert(!Disposer.isDisposed(holder))
        Disposer.register(holder, () => delegates.remove(holder))

        new UserDataHolderBase
      })
    }
  }
}
