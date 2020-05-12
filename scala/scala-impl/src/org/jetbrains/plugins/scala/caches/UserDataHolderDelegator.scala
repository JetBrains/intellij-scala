package org.jetbrains.plugins.scala
package caches

import java.util
import java.util.Collections

import com.intellij.openapi.util.{UserDataHolderBase, UserDataHolderEx}

object UserDataHolderDelegator {
  // keys are weak, so that values are let go when keys are garbage collected
  private val delegates = Collections.synchronizedMap(new util.WeakHashMap[AnyRef, UserDataHolderBase])

  def userDataHolderFor(holder: AnyRef): UserDataHolderEx =
    delegates.computeIfAbsent(holder, _ => new UserDataHolderBase)
}
