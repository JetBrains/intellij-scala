package org.jetbrains.plugins.scala.util.runners;

import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingModeTestHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AnnotatedElement;

// SCL-21849
enum TestIndexingMode {
    SMART("Smart mode", IndexingMode.SMART),
    FULL(new TestIndexingModeSupporter.FullIndexSuite()),
    RUNTIME(new TestIndexingModeSupporter.RuntimeOnlyIndexSuite()),
    EMPTY(new TestIndexingModeSupporter.EmptyIndexSuite());

    @Nullable
    private IndexingModeTestHandler handler;
    public final String label;
    public final IndexingMode mode;

    public boolean shouldIgnore(AnnotatedElement element) {
        return handler != null && handler.shouldIgnore(element);
    }

    TestIndexingMode(IndexingModeTestHandler handler) {
        this(handler.myTestNamePrefix, handler.getIndexingMode());
        this.handler = handler;
    }

    TestIndexingMode(String label, IndexingMode mode) {
        this.label = label;
        this.mode = mode;
    }
}
