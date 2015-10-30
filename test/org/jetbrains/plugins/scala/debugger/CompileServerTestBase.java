package org.jetbrains.plugins.scala.debugger;

import com.intellij.testFramework.ModuleTestCase;
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Nikolay.Tropin
 */
public abstract class CompileServerTestBase extends ModuleTestCase {

    @BeforeClass
    public static void prepareCompileServer() {
        DebuggerTestUtil.addJdk8();
        DebuggerTestUtil.setCompileServerSettings();
    }

    @AfterClass
    public static void stopCompileServer() {
        CompileServerLauncher.instance().stop();
    }

}
