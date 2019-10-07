package org.jetbrains.plugins.scala.failed.parser;

import junit.framework.Test;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * @author Nikolay.Tropin
 */
@RunWith(AllTests.class)
public abstract class FailedParserTest extends ScalaFileSetTestCase {

    public FailedParserTest() {
        super("/parser/failed");
    }

    public static Test suite() {
        return new ScalaFailedParserTest();
    }

    @Override
    protected boolean shouldPass() {
        return false;
    }
}

