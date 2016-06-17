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

public class JNDI {
  public static final String TABLE_NAME = "JNDI";

  private static final class JNDITable extends Table {
    private final StringColumn myName = new StringColumn("Name");

    public JNDITable() {
      super(JNDI.class, TABLE_NAME, Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final JNDITable T_JNDI = new JNDITable();

  @MethodPattern(
    {
      "*C*t*x*:list(javax.naming.Name) javax.naming.NamingEnumeration",
      "*C*t*x*:lookup(javax.naming.Name) Object",
      "*C*t*x*:bind(javax.naming.Name, Object) void",
      "*C*t*x*:listBindings(javax.naming.Name) javax.naming.NamingEnumeration",
      "*C*t*x*:lookupLink(javax.naming.Name) Object",
      "*C*t*x*:rebind(javax.naming.Name, Object) void",
      "*C*t*x*:unbind(javax.naming.Name) void",
      "*C*t*x*:rename(javax.naming.Name, javax.naming.Name) void",
      "*C*t*x*:composeName(javax.naming.Name, javax.naming.Name) javax.naming.Name",
      "*C*t*x*:createSubcontext(javax.naming.Name) javax.naming.Context",
      "*C*t*x*:destroySubcontext(javax.naming.Name) void",
      "*C*t*x*:getNameParser(javax.naming.Name) javax.naming.NameParser"
    }
  )
  @InstanceOf("javax.naming.Context")
  public static final class NamingContext_1 {
    public static int onEnter() {
      return T_JNDI.createRow();
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_JNDI.closeRow(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*C*t*x*:list(String) javax.naming.NamingEnumeration",
      "*C*t*x*:lookup(String) Object",
      "*C*t*x*:bind(String, Object) void",
      "*C*t*x*:listBindings(String) javax.naming.NamingEnumeration",
      "*C*t*x*:lookupLink(String) Object",
      "*C*t*x*:rebind(String, Object) void",
      "*C*t*x*:unbind(String) void"
    }
  )
  @InstanceOf("javax.naming.Context")
  public static final class NamingContext_2 {
    public static int onEnter(@Param(1) final String str) {
      final int rowIndex = T_JNDI.createRow();
      if (str != null && str.length() > 0) {
        T_JNDI.myName.setValue(rowIndex, str);
      }
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_JNDI.closeRow(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*C*t*x*:search(String, *) javax.naming.NamingEnumeration",
      "*C*t*x*:getAttributes(String) javax.naming.directory.Attributes",
      "*C*t*x*:getAttributes(String, String[]) javax.naming.directory.Attributes",
      "*C*t*x*:bind(String, Object, javax.naming.directory.Attributes) void",
      "*C*t*x*:createSubcontext(String, javax.naming.directory.Attributes) javax.naming.directory.DirContext",
      "*C*t*x*:rebind(String, Object, javax.naming.directory.Attributes) void",
      "*C*t*x*:getSchema(String) javax.naming.directory.DirContext",
      "*C*t*x*:getSchemaClassDefinition(String) javax.naming.directory.DirContext",
      "*C*t*x*:modifyAttributes(String, *) void"
    }
  )
  @InstanceOf("javax.naming.directory.DirContext")
  public static final class DirContext_1 {
    public static int onEnter(@Param(1) final String str) {
      final int rowIndex = T_JNDI.createRow();
      if (str != null && str.length() > 0) {
        T_JNDI.myName.setValue(rowIndex, str);
      }
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_JNDI.closeRow(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*C*t*x*:search(javax.naming.Name, *) javax.naming.NamingEnumeration",
      "*C*t*x*:getAttributes(javax.naming.Name, String[]) javax.naming.directory.Attributes",
      "*C*t*x*:getAttributes(javax.naming.Name) javax.naming.directory.Attributes",
      "*C*t*x*:bind(javax.naming.Name, Object, javax.naming.directory.Attributes) void",
      "*C*t*x*:createSubcontext(javax.naming.Name, javax.naming.directory.Attributes) javax.naming.directory.DirContext",
      "*C*t*x*:rebind(javax.naming.Name, Object, javax.naming.directory.Attributes) void",
      "*C*t*x*:getSchema(javax.naming.Name) javax.naming.directory.DirContext",
      "*C*t*x*:getSchemaClassDefinition(javax.naming.Name) javax.naming.directory.DirContext",
      "*C*t*x*:modifyAttributes(javax.naming.Name, *) void"
    }
  )
  @InstanceOf("javax.naming.directory.DirContext")
  public static final class DirContext_2 {
    public static int onEnter() {
      return T_JNDI.createRow();
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_JNDI.closeRow(rowIndex, e);
    }
  }
}
