package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

/**
 * @author Nikolay.Tropin
 */
public class TypeText {
    private static class TypeTextTable extends Table {
        private StringColumn result = new StringColumn("Result");
        private StringColumn methodName = new StringColumn("Method Name");

        public TypeTextTable() {
            super(ResolveProbes.class, "Type text", Table.LASTING_EVENTS + Table.RECORD_THREAD);
            setMinimumRecordedLastingEventTime(1);
        }
    }
    private static final TypeTextTable TABLE = new TypeTextTable();


    @MethodPattern({
            "org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation$class:presentableText(*)",
            "org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation$class:canonicalText(*)"
    })
    public static class Probe {
        public static int onEnter() {
            return TABLE.createRow();
        }

        public static void onExit(
                @OnEnterResult final int rowIndex,
                @ReturnValue final String result,
                @MethodName final String methodName,
                @ThrownException  final Throwable e
        ) {
            if (e == null) {
                TABLE.result.setValue(rowIndex, result);
                TABLE.methodName.setValue(rowIndex, methodName);
            }
            TABLE.closeRow(rowIndex, e);
        }
    }
}
