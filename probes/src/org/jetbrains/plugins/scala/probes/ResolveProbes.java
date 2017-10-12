package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.*;

import java.lang.reflect.Array;

/**
 * @author Nikolay.Tropin
 */
public class ResolveProbes {
    private static class ResolveTable extends Table {
        public final int minRecordMs = 2;
        public final int minStacktraceMs = 10;

        private StringColumn refName = new StringColumn("RefName");
        private StringColumn target = new StringColumn("Target");
        private LongColumn noGcTime = new LongColumn("No GC time");

        private StringColumn stacktrace = new StringColumn("Stacktrace");

        public ResolveTable() {
            super(ResolveProbes.class, "Resolve", Table.LASTING_EVENTS | Table.RECORD_THREAD);
            setMinimumRecordedLastingEventTime(minRecordMs);
        }
    }
    private static final ResolveTable TABLE = new ResolveTable();


    @MethodPattern({
            "org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceExpression$class:multiResolve(*)",
            "org.jetbrains.plugins.scala.lang.resolve.ResolvableStableCodeReferenceElement$class:multiResolve(*)"
    })
    public static class MultiResolveProbe {
        public static Pair<Integer, Long> onEnter() {
            return new Pair<Integer, Long>(TABLE.createRow(), Utilities.gcTime());
        }

        public static void onExit(
                @MethodTimeMs final long duration,
                @OnEnterResult final Pair<Integer, Long> rowAndGc,
                @Param(1) final Object ref,
                @Param(2) final boolean incomplete,
                @ReturnValue final Object resolveResults,
                @ThrownException  final Throwable e
        ) {
            int rowIndex = rowAndGc.first;
            long gcTime = Utilities.gcTime() - rowAndGc.second;
            long noGcDuration = duration - gcTime;
            if (noGcDuration >= TABLE.minRecordMs) {
                TABLE.refName.setValue(rowIndex, Utilities.refName(ref));
                TABLE.noGcTime.setValue(rowIndex, noGcDuration);
                if (e == null) {
                    int length = Array.getLength(resolveResults);
                    if (length > 0) {
                        Object rr = Array.get(resolveResults, 0);
                        TABLE.target.setValue(rowIndex, Utilities.getTargetName(rr));
                    }
                    else {
                        TABLE.target.setValue(rowIndex, "No resolve");
                    }
                }
                if (noGcDuration >= TABLE.minStacktraceMs) {
                    TABLE.stacktrace.setValue(rowIndex, Utilities.currentStackTrace());
                }
            }
            TABLE.closeRow(rowIndex, e);
        }
    }
}
