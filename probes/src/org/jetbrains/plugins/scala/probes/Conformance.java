package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

/**
 * @author Nikolay.Tropin
 */
public class Conformance {
    private static class ConformanceTable extends Table {
        public final int minRecordMs = 2;
        public final int minStacktraceMs = 10;

        private StringColumn leftType = new StringColumn("Left");
        private StringColumn rightType = new StringColumn("Right");
        private StringColumn result = new StringColumn("Result");
        private LongColumn noGcTime = new LongColumn("No GC time");

        private StringColumn stacktrace = new StringColumn("Stacktrace");

        public ConformanceTable() {
            super(Conformance.class, "Conformance", Table.LASTING_EVENTS | Table.RECORD_THREAD);
            setMinimumRecordedLastingEventTime(minRecordMs);
        }
    }
    private static final ConformanceTable TABLE = new ConformanceTable();

    @MethodPattern({
            "org.jetbrains.plugins.scala.lang.psi.types.api.Conformance$class:conformsInner(*)"
    })
    public static final class Probe {
        public static Pair<Integer, Long> onEnter() {
            return new Pair<Integer, Long>(TABLE.createRow(), Utilities.gcTime());
        }

        public static void onExit(
                @MethodTimeMs final long duration,
                @OnEnterResult final Pair<Integer, Long> rowAndGc,
                @ReturnValue final Object tuple,
                @Param(2) final Object left,
                @Param(3) final Object right,
                @ThrownException final Throwable e
        ) {
            int rowIndex = rowAndGc.first;
            long gcTime = Utilities.gcTime() - rowAndGc.second;
            long noGcDuration = duration - gcTime;
            if (noGcDuration >= TABLE.minRecordMs) {
                TABLE.leftType.setValue(rowIndex, Utilities.presentableText(left));
                TABLE.rightType.setValue(rowIndex, Utilities.presentableText(right));
                TABLE.noGcTime.setValue(rowIndex, noGcDuration);
                if (e == null) {
                    TABLE.result.setValue(rowIndex, Utilities.firstComponentText(tuple));
                }
                if (noGcDuration >= TABLE.minStacktraceMs) {
                    TABLE.stacktrace.setValue(rowIndex, Utilities.currentStackTrace());
                }
            }
            TABLE.closeRow(rowIndex, e);
        }

    }
}
