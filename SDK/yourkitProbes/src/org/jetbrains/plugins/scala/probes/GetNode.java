package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

/**
 * @author Nikolay.Tropin
 */
public class GetNode {
    private static class TypeTextTable extends Table {
        public final int minRecordMs = 2;
        public final int minStacktraceMs = 10;

        private StringColumn element = new StringColumn("Element");
        private StringColumn stacktrace = new StringColumn("Stacktrace");
        private LongColumn noGcTime = new LongColumn("No GC time");


        public TypeTextTable() {
            super(ResolveProbes.class, "getNode on Stub", Table.LASTING_EVENTS + Table.RECORD_THREAD);
            setMinimumRecordedLastingEventTime(minRecordMs);
        }
    }
    private static final TypeTextTable TABLE = new TypeTextTable();


    @MethodPattern({"com.intellij.extapi.psi.StubBasedPsiElementBase:getNode(*)"})
    public static class Probe {
        public static Pair<Integer, Long> onEnter(@This final Object element) {
            int row = Table.NO_ROW;
            long gcTime = -1;
            if (Utilities.isStub(element)) {
                row = TABLE.createRow();
                gcTime = Utilities.gcTime();
            }
            return new Pair<Integer, Long>(row, gcTime);
        }

        public static void onExit(
                @This final Object element,
                @MethodTimeMs final long duration,
                @OnEnterResult final Pair<Integer, Long> rowAndGc,
                @ThrownException final Throwable e
        ) {
            int rowIndex = rowAndGc.first;

            if (rowIndex == Table.NO_ROW) return;

            long gcTime = Utilities.gcTime() - rowAndGc.second;
            long noGcDuration = duration - gcTime;

            if (noGcDuration >= TABLE.minRecordMs) {
                TABLE.noGcTime.setValue(rowIndex, noGcDuration);
                TABLE.element.setValue(rowIndex, Utilities.toString(element));
                if (noGcDuration >= TABLE.minStacktraceMs) {
                    TABLE.stacktrace.setValue(rowIndex, Utilities.currentStackTrace());
                }
            }
            TABLE.closeRow(rowIndex, e);
        }
    }
}
