package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

/**
 * @author Nikolay.Tropin
 */
public class GetType {
    private static class GetTypeTable extends Table {
        public final int minRecordMs = 2;
        public final int minStacktraceMs = 10;

        private StringColumn exprText = new StringColumn("PsiElement");
        private StringColumn methodName = new StringColumn("Method");
        private LongColumn noGcTime = new LongColumn("No GC time");

        private StringColumn computedType = new StringColumn("Computed type");
        private StringColumn stacktrace = new StringColumn("Stacktrace");

        public GetTypeTable() {
            super(GetType.class, "getType", Table.LASTING_EVENTS | Table.RECORD_THREAD);
            setMinimumRecordedLastingEventTime(minRecordMs);
        }

    }
    private static final GetTypeTable TABLE = new GetTypeTable();

    @MethodPattern({
            "org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression$class:getType(*)",
            "org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression$class:expectedType(*)",
            "org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement$class:getType(*)"
    })
    public static final class Probe {
        public static Pair<Integer, Long> onEnter() {
            return new Pair<Integer, Long>(TABLE.createRow(), Utilities.gcTime());
        }

        public static void onExit(
                @MethodTimeMs final long duration,
                @MethodName final String mName,
                @OnEnterResult final Pair<Integer, Long> rowAndGc,
                @Param(1) final Object elem,
                @ReturnValue final Object typeResult,
                @ThrownException  final Throwable e
        ) {
            int rowIndex = rowAndGc.first;
            long gcTime = Utilities.gcTime() - rowAndGc.second;
            long noGcDuration = duration - gcTime;
            if (noGcDuration >= TABLE.minRecordMs) {
                TABLE.exprText.setValue(rowIndex, Utilities.getText(elem));
                TABLE.methodName.setValue(rowIndex, mName);
                TABLE.noGcTime.setValue(rowIndex, noGcDuration);
                if (e == null) {
                    TABLE.computedType.setValue(rowIndex, Utilities.presentableTextFromTypeResult(typeResult));
                }
                if (noGcDuration >= TABLE.minStacktraceMs) {
                    TABLE.stacktrace.setValue(rowIndex, Utilities.currentStackTrace());
                }
            }
            TABLE.closeRow(rowIndex, e);
        }
    }
}
