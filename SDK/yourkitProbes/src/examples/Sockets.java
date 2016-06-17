/*
  Copyright (c) 2003-2016, YourKit
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of YourKit nor the
    names of its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY YOURKIT "AS IS" AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL YOURKIT BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package examples;

import com.yourkit.probes.*;
import com.yourkit.runtime.Callback;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public final class Sockets {
  public static final String TABLE_NAME = "Socket";

  /**
   * Resource can be Socket or directly opened SocketChannel
   */
  private static final MasterResourceRegistry<Object> ourSockets = new MasterResourceRegistry<Object>(
    Sockets.class,
    TABLE_NAME,
    null,
    "Address" // column name
  ) {
        @Override
    protected String retrieveResourceName(final Object resource) throws Exception {
      final Socket socket = (Socket)(resource instanceof SocketChannel ? ((SocketChannel)resource).socket() : resource);
      return
        "[remote: " +
        getPresentableAddress(socket.getRemoteSocketAddress()) +
        ", local: " +
        getPresentableAddress(socket.getLocalSocketAddress()) +
        "]";
    }
  };

  private static final TableIntBytes T_STREAM_READ = new TableIntBytes(ourSockets, "Read", Table.MASK_FOR_LASTING_EVENTS);
  private static final TableIntBytes T_STREAM_WRITE = new TableIntBytes(ourSockets, "Write", Table.MASK_FOR_LASTING_EVENTS);

  private static final TableLongBytes T_CHANNEL_READ = new TableLongBytes(ourSockets, "Channel Read", Table.MASK_FOR_LASTING_EVENTS);
  private static final TableLongBytes T_CHANNEL_WRITE = new TableLongBytes(ourSockets, "Channel Write", Table.MASK_FOR_LASTING_EVENTS);

  /**
   * Associate input streams with socket
   */
  @MethodPattern("java.net.Socket:getInputStream()")
  public static final class Socket_getInputStream_Probe {
    public static void onReturn(
      @ReturnValue  final InputStream stream,
      @This final Socket socket
    ) {
      // associate stream with socket resource
      ourSockets.mapAlias(socket, stream);
    }
  }

  /**
   * Associate output streams with socket
   */
  @MethodPattern("java.net.Socket:getOutputStream()")
  public static final class Socket_getOutputStream_Probe {
    public static void onReturn(
      @ReturnValue  final OutputStream stream,
      @This final Socket socket
    ) {
      // associate stream with socket resource
      ourSockets.mapAlias(socket, stream);
    }
  }

  /**
   * Record individual read event
   */
  @MethodPattern(
    "java.net.SocketInputStream:read(byte[], int, int)"
  )
  public static final class SocketInputStream_read_Probe {
    public static int onEnter(@This final InputStream stream) {
      final int socketRowIndex = getOrCreateSocketRowFromStream(stream);
      return T_STREAM_READ.createRow(socketRowIndex);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int bytesRead,
      @ThrownException  final Throwable e
    ) {
      T_STREAM_READ.setBytes(readRowIndex, bytesRead);
      T_STREAM_READ.closeRow(readRowIndex, e);
    }
  }

  @MethodPattern("java.net.ServerSocket:accept()")
  public static final class ServerSocket_accept_Probe {
    public static void onReturn(@ReturnValue  final Socket socket) {
      if (socket == null) {
        return;
      }
      onSocketAccepted(socket, socket.getLocalSocketAddress());
    }
  }

  private static void onSocketAccepted( final Object socket,  final SocketAddress address) {
    // Open as a lasting event, but we are not interested in the time spent in accept().
    // Instead, let's make it as short as possible by emulating enter and exit:

    final long resourceID = ourSockets.openOnEnter();
    ourSockets.openOnExit(
      resourceID,
      getPresentableAddress(address),
      socket,
      null, // no exception
      FailedEventPolicy.DISCARD // use any value - it will not be used if exception is null
    );
  }

  @MethodPattern("java.net.Socket:connect(java.net.SocketAddress, int)")
  public static final class Socket_connect_Probe {
    public static long onEnter() {
      return ourSockets.openOnEnter();
    }

    public static void onExit(
      @This final Socket socket,
      @Param(1) final SocketAddress address,
      @OnEnterResult final long resourceID,
      @ThrownException  final Throwable e
    ) {
      ourSockets.openOnExit(
        resourceID,
        getPresentableAddress(address),
        socket,
        e,
        FailedEventPolicy.RECORD
      );
    }
  }

  /**
   * Record individual write event
   */
  @MethodPattern("java.net.SocketOutputStream:socketWrite(byte[], int, int)")
  public static final class SocketOutputStream_write_Probe {
    public static int onEnter(
      @This final OutputStream stream,
      @Param(3) final int writtenBytes
    ) {
      final int socketRowIndex = getOrCreateSocketRowFromStream(stream);
      if (Table.shouldIgnoreRow(socketRowIndex)) {
        // optimization
        return Table.NO_ROW;
      }

      final int rowIndex = T_STREAM_WRITE.createRow(socketRowIndex);
      T_STREAM_WRITE.setBytes(rowIndex, writtenBytes);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_STREAM_WRITE.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("sun.nio.ch.ServerSocketChannelImpl:accept()")
  public static final class ServerSocketChannel_accept_Probe {
    public static void onReturn(@ReturnValue  final SocketChannel socketChannel) {
      if (socketChannel == null) {
        return;
      }

      final Socket socket = socketChannel.socket();
      if (socket == null) {
        return;
      }

      onSocketAccepted(socket, socket.getLocalSocketAddress());

      // associate channel with socket resource
      ourSockets.mapAlias(socket, socketChannel);
    }
  }

  @MethodPattern("sun.nio.ch.SocketChannelImpl:connect(java.net.SocketAddress)")
  public static final class SocketChannel_connect_Probe {
    public static long onEnter() {
      return ourSockets.openOnEnter();
    }

    public static void onExit(
      @This final SocketChannel socketChannel,
      @Param(1) final SocketAddress address,
      @OnEnterResult final long resourceID,
      @ThrownException  final Throwable e
    ) {
      ourSockets.openOnExit(
        resourceID,
        getPresentableAddress(address),
        socketChannel,
        e,
        FailedEventPolicy.RECORD
      );

      // associate socket with channel
      ourSockets.mapAlias(socketChannel, socketChannel.socket());
    }
  }

  private static String getPresentableAddress( final SocketAddress address) {
    return address != null ? address.toString() : null;
  }

  private static int getOrCreateSocketRowFromChannel(final SocketChannel channel) {
    final int rowIndex = ourSockets.get(channel); // get existing association
    if (!Table.shouldIgnoreRow(rowIndex)) {
      return rowIndex;
    }
    final int newRowIndex = ourSockets.getOrCreate(channel);
    ourSockets.mapAlias(channel, channel.socket());
    return newRowIndex;
  }

  private static int getOrCreateSocketRowFromStream(final Object stream) {
    final int rowIndex = ourSockets.get(stream); // get by alias
    if (!Table.shouldIgnoreRow(rowIndex)) {
      return rowIndex;
    }
    Socket socket = null;
    try {
      socket = Callback.getFieldObjectValue(stream, "socket", Socket.class);
    }
    catch (final Throwable ignored) {
    }
    if (socket == null) {
      return Table.NO_ROW;
    }

    final int newRowIndex = ourSockets.getOrCreate(socket);
    ourSockets.mapAlias(socket, stream);
    return newRowIndex;
  }

  /**
   * Record individual write event
   */
  @MethodPattern(
    {
      "sun.nio.ch.SocketChannelImpl:write(java.nio.ByteBuffer) int",
      "sun.nio.ch.SocketChannelImpl:write(java.nio.ByteBuffer[], int, int) long"
    }
  )
  public static final class SocketChannel_write_Probe {
    public static int onEnter(@This final SocketChannel socketChannel) {
      final int socketRowIndex = getOrCreateSocketRowFromChannel(socketChannel);

      return T_CHANNEL_WRITE.createRow(socketRowIndex);
    }

    public static void onExit(
      @OnEnterResult final int writeRowIndex,
      @ReturnValue final long bytesWritten,
      @ThrownException  final Throwable e
    ) {
      if (Table.shouldIgnoreRow(writeRowIndex)) {
        // optimization
        return;
      }

      T_CHANNEL_WRITE.setBytes(writeRowIndex, bytesWritten);
      T_CHANNEL_WRITE.closeRow(writeRowIndex, e);
    }
  }

  /**
   * Record individual read event
   */
  @MethodPattern(
    {
      "sun.nio.ch.SocketChannelImpl:read(java.nio.ByteBuffer) int",
      "sun.nio.ch.SocketChannelImpl:read(java.nio.ByteBuffer[], int, int) long"
    }
  )
  public static final class SocketChannel_read_Probe {
    public static int onEnter(@This final SocketChannel socketChannel) {
      final int rowIndex = getOrCreateSocketRowFromChannel(socketChannel);

      return T_CHANNEL_READ.createRow(rowIndex);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final long bytesRead,
      @ThrownException  final Throwable e
    ) {
      if (Table.shouldIgnoreRow(readRowIndex)) {
        // optimization
        return;
      }

      T_CHANNEL_READ.setBytes(readRowIndex, bytesRead);
      T_CHANNEL_READ.closeRow(readRowIndex, e);
    }
  }

  // Close socket or channel

  @MethodPattern("java.net.Socket:close()")
  public static final class Socket_close_Probe {
    public static int onEnter(@This final Socket socket) {
      return ourSockets.closeOnEnter(socket);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      ourSockets.closeOnExit(rowIndex, e);
    }
  }

  @MethodPattern("sun.nio.ch.SocketChannelImpl:implCloseSelectableChannel()")
  public static final class SocketChannel_close_Probe {
    public static int onEnter(@This final SocketChannel channel) {
      return ourSockets.closeOnEnter(channel);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      ourSockets.closeOnExit(rowIndex, e);
    }
  }
}
