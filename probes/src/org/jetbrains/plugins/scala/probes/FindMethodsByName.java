package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

/**
 * @author Nikolay.Tropin
 */
public class FindMethodsByName {
    public static final String TABLE_NAME = "Find method by name";

    private static final class ResultsTable extends Table {
        private final StringColumn myMethodName = new StringColumn("Method");
        private final StringColumn myClassName = new StringColumn("Class");

        public ResultsTable() {
            super(FindMethodsByName.class, TABLE_NAME, Table.LASTING_EVENTS | Table.RECORD_THREAD);
        }
    }
    private static final ResultsTable T_RESULTS = new ResultsTable();

    @MethodPattern({"org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl:findMethodsByName(*)"})
    public static final class Probe {
        public static int onEnter() {
            return T_RESULTS.createRow();
        }

        public static void onExit(
                @Param(1) final String methodName,
                @This final Object typeDef,
                @OnEnterResult final int rowIndex,
                @ThrownException  final Throwable e
        ) {
            T_RESULTS.myMethodName.setValue(rowIndex, methodName);
            T_RESULTS.myClassName.setValue(rowIndex, Utilities.name(typeDef));
            T_RESULTS.closeRow(rowIndex, e);
        }
    }
}
