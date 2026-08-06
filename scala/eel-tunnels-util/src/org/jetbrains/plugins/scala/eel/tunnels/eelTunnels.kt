@file:Suppress("UnstableApiUsage", "OPT_IN_USAGE")
@file:JvmName("EelTunnels")

package org.jetbrains.plugins.scala.eel.tunnels

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.acceptOnTcpPort
import com.intellij.platform.eel.provider.utils.connectToTcpPort
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus

private fun CoroutineScope.forwardLocalPortImpl(tunnels: EelTunnelsApi, remotePort: Int): Deferred<Int> {
  return async {
    try {
      val address = EelTunnelsApi.HostAddress.Builder(remotePort.toUShort()).build()
      val proxy = eelProxy()
        .acceptOnTcpPort(localEel.tunnels, port = 0u)
        .connectToTcpPort(tunnels, host = address.hostname, port = address.port)
        .eelIt()

      this@forwardLocalPortImpl.launch {
        proxy.runForever()
      }

      proxy.acceptor.boundAddress.port.toInt()
    } catch (t: EelConnectionError) {
      t.printStackTrace()
      this@async.cancel()
      ensureActive()
      error("unreachable")
    }
  }
}

@ApiStatus.Internal
internal fun forwardLocalPort(scope: CoroutineScope, tunnels: EelTunnelsApi, remotePort: Int): Int {
  return scope.forwardLocalPortImpl(tunnels, remotePort).asCompletableFuture().get()
}
