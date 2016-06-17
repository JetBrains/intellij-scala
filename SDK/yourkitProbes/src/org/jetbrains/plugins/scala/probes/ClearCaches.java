package org.jetbrains.plugins.scala.probes;

import com.yourkit.probes.MethodName;
import com.yourkit.probes.MethodPattern;
import com.yourkit.probes.StringColumn;
import com.yourkit.probes.Table;

/**
 * @author Nikolay.Tropin
 */
public class ClearCaches {
    private static class CachesTable extends Table {
        private StringColumn methodName = new StringColumn("Method name");

        public CachesTable() {
            super(ClearCaches.class, "Clear caches", Table.MASK_FOR_POINT_EVENTS);
        }
    }
    private static final CachesTable TABLE = new CachesTable();

    @MethodPattern("org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager:clear*(*)")
    public static final class Probe {
        public static void onEnter(
                @MethodName String mName
        ) {
            final int rowIndex = TABLE.createRow();
            TABLE.methodName.setValue(rowIndex, mName);
        }
    }
}
