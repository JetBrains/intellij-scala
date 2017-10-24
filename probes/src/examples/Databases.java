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

import javax.sql.DataSource;
import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;

public final class Databases {
  public static final String TABLE_NAME = "Database";

  private static final Object ourLock = new Object();

  private static final WeakKeyMap<DataSource, String> ourDataSource2Context = new WeakKeyMap<DataSource, String>();

  /**
   * Non-prepared statement's batch
   */
  private static final WeakKeyMap<Statement, StringBuilder> ourStatement2Batch = new WeakKeyMap<Statement, StringBuilder>();

  private static final MasterResourceRegistry<Connection> ourConnections = new MasterResourceRegistry<Connection>(
    Databases.class,
    "Database",
    "Database connection",
    "Database" // column name
  ) {
    @Override
    protected String retrieveResourceName(final Connection connection) throws Exception {
      final DatabaseMetaData metaData = connection.getMetaData();
      return metaData == null ? null : metaData.getURL();
    }
  };

  private static final DependentResourceRegistry<Statement, Connection> ourStatements = new DependentResourceRegistry<Statement, Connection>(
    ourConnections,
    "Statement",
    "Database statement (not prepared)",
    null // we don't need a string column to identify non-prepared statements
  ) {
    @Override
    protected Connection retrieveParent(final Statement resource) throws Exception {
      return resource.getConnection();
    }
  };

  private static final class QueryTable extends Table {
    private final StringColumn mySQL = new StringColumn("SQL");

    public QueryTable() {
      super(ourStatements.getResourceTable(), "Query", Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final QueryTable T_QUERY = new QueryTable();

  private static final DependentResourceRegistry<PreparedStatement, Connection> ourPreparedStatements = new DependentResourceRegistry<PreparedStatement, Connection>(
    ourConnections,
    "Prepared Statement",
    "Database prepared statement",
    "SQL" // column name
  ) {
    @Override
    protected Connection retrieveParent(final PreparedStatement resource) throws Exception {
      return resource.getConnection();
    }
  };

  private static final class PreparedStatementQueryTable extends Table {
    public PreparedStatementQueryTable() {
      super(ourPreparedStatements.getResourceTable(), "Query", Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final PreparedStatementQueryTable T_PREPARED_STATEMENT_QUERY = new PreparedStatementQueryTable();


  private static final String BATCH_OPERATION_ADD = "Add";
  private static final String BATCH_OPERATION_CLEAR = "Clear";

  private static final class PreparedStatementBatchTable extends Table {
    private final StringColumn myOperation = new StringColumn("Operation");

    public PreparedStatementBatchTable() {
      super(ourPreparedStatements.getResourceTable(), "Batch", Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final PreparedStatementBatchTable T_PREPARED_STATEMENT_BATCH = new PreparedStatementBatchTable();

  private static final class BatchTable extends Table {
    private final StringColumn myOperation = new StringColumn("Operation");
    private final StringColumn mySQL = new StringColumn("SQL");

    public BatchTable() {
      super(ourStatements.getResourceTable(), "Batch", Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final BatchTable T_BATCH = new BatchTable();


  @MethodPattern(
    {
      "java.sql.DriverManager:getConnection(String, java.util.Properties, java.lang.ClassLoader)",
      "-org.springframework.jdbc.*:*(*)"
    }
  )
  public static final class DriverManager_getConnection_Probe {
    public static long onEnter() {
      return ourConnections.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @ReturnValue  final Connection connection,
      @ThrownException  final Throwable e,
      @Param(1) final String database
    ) {
      ourConnections.openOnExit(
        resourceID,
        database,
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  @MethodPattern(
    {
      "*:connect(String, java.util.Properties) java.sql.Connection",
      "-org.springframework.jdbc.*:*(*)"
    }
  )
  @InstanceOf("java.sql.Driver")
  public static final class Driver_connect_Probe {
    public static long onEnter() {
      return ourConnections.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @Param(1) final String url,
      @ThrownException  final Throwable e,
      @ReturnValue  final Connection connection
    ) {
      ourConnections.openOnExit(
        resourceID,
        url,
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  @MethodPattern("javax.naming.InitialContext:lookup(String)")
  public static final class InitialContext_lookup_Probe {
    public static void onReturn(
      @ReturnValue  final Object returnValue,
      @Param(1) final String context
    ) {
      if (context != null && (returnValue instanceof DataSource)) {
        synchronized (ourLock) {
          ourDataSource2Context.put((DataSource)returnValue, context);
        }
      }
    }
  }

  @MethodPattern(
    {
      "org.apache.commons.dbcp.BasicDataSource:setUrl(String)",
      "org.apache.derby.jdbc.ClientBaseDataSource:setDatabaseName(String)"
    }
  )
  public static final class DataSource_setName_Probe {
    public static void onReturn(
      @This final DataSource dataSource,
      @Param(1) final String url
    ) {
      if (url == null) {
        return;
      }
      synchronized (ourLock) {
        ourDataSource2Context.put(dataSource, url);
      }
    }
  }

  @MethodPattern(
    {
      "*:getConnection() java.sql.Connection",
      "-org.springframework.jdbc.*:*(*)"
    }
  )
  public static final class DataSource_getConnection_Probe {
    public static long onEnter() {
      return ourConnections.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @This final DataSource dataSource,
      @ReturnValue  final Connection connection,
      @ThrownException  final Throwable e
    ) {
      final String nameFromDataSource;
      synchronized (ourLock) {
        nameFromDataSource = ourDataSource2Context.get(dataSource);
      }

      ourConnections.openOnExit(
        resourceID,
        nameFromDataSource,
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  @MethodPattern(
    {
      "*:getConnection() java.sql.Connection",
      "-org.springframework.jdbc.*:*(*)"
    }
  )
  public static final class PooledConnection_getConnection_Probe {
    public static long onEnter(@This final PooledConnection pooledConnection) {
      if (pooledConnection instanceof DataSource) {
        return Table.NO_ROW;
      }

      return ourConnections.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @ReturnValue  final Connection connection,
      @ThrownException  final Throwable e
    ) {
      ourConnections.openOnExit(
        resourceID,
        null, // the name will be obtained with retrieveResourceName()
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  @MethodPattern(
    {
      "*:execute(String, int) boolean",
      "*:execute(String) boolean",
      "*:execute(String, int[]) boolean",
      "*:execute(String, String[]) boolean",
      "*:executeQuery(String) java.sql.ResultSet",
      "*:executeUpdate(String, int[]) int",
      "*:executeUpdate(String) int",
      "*:executeUpdate(String, int) int",
      "*:executeUpdate(String, String[]) int"
    }
  )
  public static final class Statement_execute_Probe {
    public static int onEnter(
      @This final Statement statement,
      @Param(1) final String query
    ) {
      if (statement instanceof PreparedStatement) {
        return Table.NO_ROW;
      }

      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      final int rowIndex = T_QUERY.createRow(statementRowIndex);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return Table.NO_ROW;
      }

      T_QUERY.mySQL.setValue(rowIndex, query == null ? "<unknown>" : query);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_QUERY.closeRow(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*:execute() boolean",
      "*:executeQuery() java.sql.ResultSet",
      "*:executeUpdate() int"
    }
  )
  public static final class PreparedStatement_execute_Probe {
    public static int onEnter(@This final PreparedStatement statement) {
      final int statementRowIndex = getOrCreateStatement(statement);
      return T_PREPARED_STATEMENT_QUERY.createRow(statementRowIndex);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_PREPARED_STATEMENT_QUERY.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:addBatch(String) void")
  public static final class Statement_addBatch_Probe {
    public static int onEnter(
      @This final Statement statement,
      @Param(1) final String query
    ) {
      if (statement instanceof PreparedStatement) {
        return Table.NO_ROW;
      }

      // add the query to the batch
      synchronized (ourStatement2Batch) {
        StringBuilder batch = ourStatement2Batch.get(statement);
        if (batch == null) {
          batch = new StringBuilder();
          ourStatement2Batch.put(statement, batch);
        }
        if (batch.length() != 0) {
          batch.append("\n");
        }
        batch.append(query);
      }

      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      final int rowIndex = T_BATCH.createRow(statementRowIndex);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return Table.NO_ROW;
      }

      T_BATCH.mySQL.setValue(rowIndex, query);
      T_BATCH.myOperation.setValue(rowIndex, BATCH_OPERATION_ADD);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_BATCH.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:addBatch() void")
  public static final class PreparedStatement_addBatch_Probe {
    public static int onEnter(@This final PreparedStatement statement) {
      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      final int rowIndex = T_PREPARED_STATEMENT_BATCH.createRow(statementRowIndex);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return Table.NO_ROW;
      }

      T_PREPARED_STATEMENT_BATCH.myOperation.setValue(rowIndex, BATCH_OPERATION_ADD);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_PREPARED_STATEMENT_BATCH.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:executeBatch() int[]")
  public static final class Statement_executeBatch_Probe {
    public static int onEnter(@This final Statement statement) {
      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      if (statement instanceof PreparedStatement) {
        return T_PREPARED_STATEMENT_QUERY.createRow(statementRowIndex);
      }
      else {
        final int rowIndex = T_QUERY.createRow(statementRowIndex);

        final StringBuilder builder;
        synchronized (ourStatement2Batch) {
          // JDBC specification:
          // "The statement's batch is reset to empty once executeBatch returns."

          builder = ourStatement2Batch.remove(statement);
        }

        if (builder != null) {
          T_QUERY.mySQL.setValue(rowIndex, builder.toString());
        }
        return rowIndex;
      }
    }

    public static void onExit(
      @This final Statement statement,
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      if (statement instanceof PreparedStatement) {
        T_PREPARED_STATEMENT_QUERY.closeRow(rowIndex, e);
      }
      else {
        T_QUERY.closeRow(rowIndex, e);
      }
    }
  }

  @MethodPattern("*:clearBatch() void")
  public static final class Statement_clearBatch_Probe {
    public static int onEnter(@This final Statement statement) {
      if (statement instanceof PreparedStatement) {
        return Table.NO_ROW;
      }

      synchronized (ourStatement2Batch) {
        ourStatement2Batch.remove(statement);
      }

      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      final int rowIndex = T_BATCH.createRow(statementRowIndex);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return Table.NO_ROW;
      }

      T_BATCH.mySQL.setValue(rowIndex, "");
      T_BATCH.myOperation.setValue(rowIndex, BATCH_OPERATION_CLEAR);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_BATCH.closeRow(rowIndex, e);
    }
  }

  @MethodPattern("*:clearBatch() void")
  public static final class PreparedStatement_clearBatch_Probe {
    public static int onEnter(@This final PreparedStatement statement) {
      final int statementRowIndex = getOrCreateStatement(statement);
      if (Table.shouldIgnoreRow(statementRowIndex)) {
        return Table.NO_ROW;
      }

      final int rowIndex = T_PREPARED_STATEMENT_BATCH.createRow(statementRowIndex);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return Table.NO_ROW;
      }

      T_PREPARED_STATEMENT_BATCH.myOperation.setValue(rowIndex, BATCH_OPERATION_CLEAR);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_PREPARED_STATEMENT_BATCH.closeRow(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*:close() void",
      "-java.io.*:*()",
      "-java.nio.*:*()",
      "-sun.nio.*:*()",
      "-java.net.*:*()",
      "-*$$Lambda$*:*()",
      "-com.sun.xml.*:*()",
      "-*xerces*:*()",
      "-*Stream*:*()",
      "-*Socket*:*()",
      "-*File*:*()"
    }
  )
  public static final class Connection_close_Probe {
    public static int onEnter(@This final Connection connection) {
      return ourConnections.closeOnEnter(connection);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      ourConnections.closeOnExit(rowIndex, e);
    }
  }

  @MethodPattern(
    {
      "*:close() void",
      "-java.io.*:*()",
      "-java.nio.*:*()",
      "-sun.nio.*:*()",
      "-java.net.*:*()",
      "-*$$Lambda$*:*()",
      "-com.sun.xml.*:*()",
      "-*xerces*:*()",
      "-*Stream*:*()",
      "-*Socket*:*()",
      "-*File*:*()"
    }
  )
  public static final class Statement_close_Probe {
    public static int onEnter(@This final Statement statement) {
      if (statement instanceof PreparedStatement) {
        return ourPreparedStatements.closeOnEnter((PreparedStatement)statement);
      }
      else {
        return ourStatements.closeOnEnter(statement);
      }
    }

    public static void onExit(
      @This final Statement statement,
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      if (statement instanceof PreparedStatement) {
        ourPreparedStatements.closeOnExit(rowIndex, e);
      }
      else {
        ourStatements.closeOnExit(rowIndex, e);
      }
    }
  }

  @MethodPattern(
    {
      "*:createStatement(int, int, int) java.sql.Statement",
      "*:createStatement(int, int) java.sql.Statement",
      "*:createStatement() java.sql.Statement"
    }
  )
  public static final class Connection_createStatement_Probe {
    public static long onEnter() {
      return ourStatements.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @This final Connection connection,
      @ReturnValue  final Statement statement,
      @ThrownException  final Throwable e
    ) {
      ourStatements.openOnExit(
        resourceID,
        "", // no name
        statement,
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  @MethodPattern(
    {
      "*:prepareCall(String, int, int, int)",
      "*:prepareCall(String, int, int)",
      "*:prepareCall(String)",
      "*:prepareStatement(String, int)",
      "*:prepareStatement(String, String[])",
      "*:prepareStatement(String, int, int, int)",
      "*:prepareStatement(String)",
      "*:prepareStatement(String, int, int)",
      "*:prepareStatement(String, int[])"
    }
  )
  public static final class Connection_prepareStatement_Probe {
    public static long onEnter() {
      return ourPreparedStatements.openOnEnter();
    }

    public static void onExit(
      @OnEnterResult final long resourceID,
      @This final Connection connection,
      @Param(1) final String query,
      @ReturnValue  final PreparedStatement preparedStatement,
      @ThrownException  final Throwable e
    ) {
      ourPreparedStatements.openOnExit(
        resourceID,
        query,
        preparedStatement,
        connection,
        e,
        FailedEventPolicy.DISCARD
      );
    }
  }

  private static int getOrCreateStatement(final Statement statement) {
    if (statement instanceof PreparedStatement) {
      return ourPreparedStatements.getOrCreate((PreparedStatement)statement);
    }
    else {
      return ourStatements.getOrCreate(statement);
    }
  }
}
