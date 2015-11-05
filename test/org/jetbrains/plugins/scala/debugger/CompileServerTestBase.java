package org.jetbrains.plugins.scala.debugger;

import com.intellij.testFramework.ModuleTestCase;
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher;
import org.junit.AfterClass;

/**
 * @author Nikolay.Tropin
 */
public abstract class CompileServerTestBase extends ModuleTestCase {

    @AfterClass
    public static void stopCompileServer() {
        CompileServerLauncher.instance().stop();
    }

}
