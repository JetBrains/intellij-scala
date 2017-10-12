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

import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;

/**
 * Detect long running AWT/Swing events
 */
public final class AWTEvents {
  public static final String TABLE_NAME = "Long AWT Event";

  private static final class EventTable extends Table {
    private final ClassNameColumn myEventClass = new ClassNameColumn("Event Class");
    private final ClassNameColumn myRunnableClass = new ClassNameColumn("Runnable Class");

    public EventTable() {
      super(
        AWTEvents.class,
        TABLE_NAME,

        // It makes no sense to record stack trace

        Table.LASTING_EVENTS |
        Table.RECORD_THREAD
      );
    }
  }
  private static final EventTable T_EVENT = new EventTable();
  static {
    // Record only events longer than 300 milliseconds
    T_EVENT.setMinimumRecordedLastingEventTime(300);
  }

  @MethodPattern("java.awt.EventQueue:dispatchEvent(java.awt.AWTEvent)")
  public static final class EventQueue_dispatchEvent_Probe {
    public static int onEnter(@Param(1) final Object event) {
      if (event instanceof InputEvent) {
        // these events are of no interest
        return Table.NO_ROW;
      }

      return T_EVENT.createRow();
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @Param(1) final Object event,
      @ThrownException  final Throwable e
    ) {
      T_EVENT.myEventClass.setValue(rowIndex, event.getClass());

      if (event instanceof InvocationEvent) {
        try {
          final Object runnableValue = Callback.getFieldObjectValue(event, "runnable", Runnable.class);
          if (runnableValue != null) {
            T_EVENT.myRunnableClass.setValue(rowIndex, runnableValue.getClass());
          }
        }
        catch (final Throwable ignored) {
        }
      }

      T_EVENT.closeRow(rowIndex, e);
    }
  }
}
