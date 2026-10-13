package org.inf.test;

import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TestLoggingListener implements TestExecutionListener {

  private record TestName(String className, String methodName) {

  }

  private final Map<String, TestName> previousTestNames = new ConcurrentHashMap<>();

  @Override
  public void testPlanExecutionStarted(final @NonNull TestPlan testPlan) {
    // Initialize the backend before parallel tests can acquire SLF4J's temporary MDC adapter.
    LoggerFactory.getILoggerFactory();
  }

  @Override
  public void executionStarted(final TestIdentifier testIdentifier) {
    if (!testIdentifier.isTest()) {
      return;
    }
    final var previousClassName = MDC.get("testClassName");
    final var previousMethodName = MDC.get("testMethodName");
    if (previousClassName != null) {
      previousTestNames.put(testIdentifier.getUniqueId(), new TestName(previousClassName, previousMethodName));
    }
    MDC.put("testClassName", getClassName(testIdentifier));
    MDC.put("testMethodName", getMethodName(testIdentifier));
  }

  @Override
  public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
    if (!testIdentifier.isTest()) {
      return;
    }
    final var previousTestName = previousTestNames.remove(testIdentifier.getUniqueId());
    if (previousTestName == null) {
      MDC.remove("testClassName");
      MDC.remove("testMethodName");
    } else {
      MDC.put("testClassName", previousTestName.className());
      MDC.put("testMethodName", previousTestName.methodName());
    }
  }

  private String getClassName(final TestIdentifier testIdentifier) {
    final var source = testIdentifier.getSource().orElse(null);
    if (source instanceof MethodSource ms) {
      return ms.getJavaClass().getSimpleName();
    } else if (source instanceof ClassSource cs) {
      return cs.getClassName();
    }
    return null;
  }

  private String getMethodName(final TestIdentifier testIdentifier) {
    if (testIdentifier.getSource().orElse(null) instanceof MethodSource source) {
      return source.getMethodName();
    }
    return testIdentifier.getDisplayName();
  }
}
