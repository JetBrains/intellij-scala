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

public final class Servlets {
  public static final String TABLE_NAME = "JSP/Servlet";

  private static final class RequestTable extends Table {
    private final StringColumn myURI = new StringColumn("URI");
    private final StringColumn myQuery = new StringColumn("Query");

    public RequestTable() {
      super(Servlets.class, TABLE_NAME, Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final RequestTable T_REQUEST = new RequestTable();

  // FIX 20150616_1 remember which servlet classes are HttpJspPage to speed up the servlet probe
  private static final ObjectRowIndexMap<Class> ourClassIsJSPCached = new ObjectRowIndexMap<Class>();

  private static boolean isJSP(final Class aClass) {
    try {
      final Class<?> jspPageClass = Class.forName("javax.servlet.jsp.HttpJspPage", false, aClass.getClassLoader());
      return jspPageClass.isAssignableFrom(aClass);
    }
    catch (final Throwable ignored) {
      return false;
    }
  }

  @MethodPattern(
    {
      "*:service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) void",
      "-javax.servlet.http.HttpServlet:*(*)",
      "-org.apache.jasper.*:*(*)"
    }
  )
  @InstanceOf("javax.servlet.http.HttpServlet")
  public static final class Servlet_service_Probe {
    public static int onEnter(
      @ClassRef final Class aClass,
      @Param(1) final Object request
    ) {
      int isJSP = ourClassIsJSPCached.get(aClass);
      if (isJSP == Table.NO_ROW) {
        isJSP = isJSP(aClass) ? -1 : -2;
        ourClassIsJSPCached.putFirst(aClass, isJSP);
      }
      if (isJSP == -1) {
        // ignore calls to Servlet's service() for jsp pages
        return Table.NO_ROW;
      }

      return createRequestRow(request);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_REQUEST.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:_jspService(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) void")
  @InstanceOf("javax.servlet.jsp.HttpJspPage")
  public static final class HttpJspPage_jspService_Probe {
    public static int onEnter(
      @Param(1) final Object request
    ) {
      return createRequestRow(request);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_REQUEST.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)")
  @InstanceOf("javax.servlet.Filter")
  public static final class Filter_doFilter_Probe {
    public static int onEnter(
      @Param(1) final Object request
    ) {
      return createRequestRow(request);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_REQUEST.closeRow(rowIndex, e);
    }
  }

  private static int createRequestRow(final Object request) {
    final int rowIndex = T_REQUEST.createRow();
    if (Table.shouldIgnoreRow(rowIndex)) {
      // optimization
      return Table.NO_ROW;
    }

    final String includeRequestURI = getRequestAttribute(request, "javax.servlet.include.request_uri");

    final String requestURI;
    final String query;
    if (includeRequestURI == null || includeRequestURI.isEmpty()) {
      requestURI = getRequestURI(request);
      query = getQuery(request);
    }
    else {
      requestURI = includeRequestURI;
      query = getRequestAttribute(request, "javax.servlet.include.query_string");
    }

    T_REQUEST.myURI.setValue(rowIndex, requestURI);
    T_REQUEST.myQuery.setValue(rowIndex, query);

    return rowIndex;
  }

  /**
   * This method uses reflection to avoid class-loading issues
   */
  private static String getQuery(final Object request) {
    if (request == null) {
      return "<unknown>";
    }
    try {
      return (String)Callback.callObjectMethod0(request.getClass(), request, "getQueryString", "()Ljava/lang/String;");
    }
    catch (final Throwable ignored) {
      return getQuery(tryGetWrappedRequest(request));
    }
  }

  /**
   * This method uses reflection to avoid class-loading issues
   */
  static String getRequestURI(final Object request) {
    if (request == null) {
      return "<unknown>";
    }
    try {
      return (String)Callback.callObjectMethod0(request.getClass(), request, "getRequestURI", "()Ljava/lang/String;");
    }
    catch (final Throwable ignored) {
      return getRequestURI(tryGetWrappedRequest(request));
    }
  }

  /**
   * This method uses reflection to avoid class-loading issues
   */
  private static String getRequestAttribute(final Object request, final String attributeName) {
    if (request == null) {
      return "<unknown>";
    }
    try {
      final Class<?> requestClass = request.getClass();
      final String result = (String)Callback.callObjectMethod1(
        requestClass, request, "getAttribute", "(Ljava/lang/String;)Ljava/lang/Object;", attributeName
      );
      if (result != null && result.startsWith("//")) {
        return result.substring(1);
      }
      return result;
    }
    catch (final Throwable ignored) {
      return getRequestAttribute(tryGetWrappedRequest(request), attributeName);
    }
  }

  /**
   * This method uses reflection to avoid class-loading issues
   */
  private static Object tryGetWrappedRequest(final Object requestWrapper) {
    try {
      return Callback.callObjectMethod0(requestWrapper.getClass(), requestWrapper, "getRequest", "()Ljavax/servlet/ServletRequest;");
    }
    catch (final Throwable ignored) {
      return null;
    }
  }
}
