package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/** Exercises failures in the actual population task submission and completion paths. */
public class GeneratorFailureTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private Map<String, String> config;
  private PrintStream originalOut;
  private ByteArrayOutputStream output;
  private File outputDirectory;

  /** Save shared settings and isolate exports and console output. */
  @Before
  public void setUp() throws Exception {
    config = new HashMap<>();
    for (String key : Config.allPropertyNames()) {
      config.put(key, Config.get(key));
    }
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    outputDirectory = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", outputDirectory.getPath());
    Config.set("exporter.metadata.export", "true");
    originalOut = System.out;
    output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output));
  }

  /** Restore global state even when an assertion fails. */
  @After
  public void tearDown() {
    System.setOut(originalOut);
    for (String key : Config.allPropertyNames()) {
      Config.remove(key);
    }
    for (Map.Entry<String, String> entry : config.entrySet()) {
      Config.set(entry.getKey(), entry.getValue());
    }
  }

  @Test
  public void generationRuntimeExceptionPropagatesAfterAllTasksFinish() throws Exception {
    assertWorkerFailure(false, new IllegalArgumentException("generation failed"));
  }

  @Test
  public void generationErrorPropagatesAfterAllTasksFinish() throws Exception {
    assertWorkerFailure(false, new AssertionError("generation failed"));
  }

  @Test
  public void snapshotRuntimeExceptionPropagatesAfterAllTasksFinish() throws Exception {
    assertWorkerFailure(true, new IllegalArgumentException("snapshot update failed"));
  }

  @Test
  public void snapshotErrorPropagatesAfterAllTasksFinish() throws Exception {
    assertWorkerFailure(true, new AssertionError("snapshot update failed"));
  }

  private void assertWorkerFailure(boolean snapshot, Throwable cause) throws Exception {
    ProbeGenerator generator = generator(snapshot, cause);
    AtomicReference<Throwable> result = new AtomicReference<>();
    Thread runner = runner(generator, result, new AtomicBoolean());
    try {
      assertTrue(generator.blocked.await(10, TimeUnit.SECONDS));
      assertTrue("run must wait for the remaining patient tasks", runner.isAlive());
    } finally {
      generator.release.countDown();
      runner.join(10000);
    }
    assertFalse("run must finish once all tasks finish", runner.isAlive());
    assertEquals(3, generator.finished.get());
    assertTrue("worker failure must reach the run caller",
        result.get() instanceof IllegalStateException);
    assertSame(cause, result.get().getCause());
    assertNoCompletionExports(generator);
  }

  @Test
  public void interruptionRestoresFlagAndDoesNotReportSuccess() throws Exception {
    ProbeGenerator generator = generator(false, null);
    AtomicReference<Throwable> result = new AtomicReference<>();
    AtomicBoolean interrupted = new AtomicBoolean();
    Thread runner = runner(generator, result, interrupted);
    try {
      assertTrue(generator.blocked.await(10, TimeUnit.SECONDS));
      runner.interrupt();
      runner.join(10000);
    } finally {
      generator.release.countDown();
      runner.join(10000);
    }
    assertFalse(runner.isAlive());
    assertTrue(result.get() instanceof IllegalStateException);
    assertTrue(result.get().getCause() instanceof InterruptedException);
    assertTrue("run must preserve the caller's interrupt status", interrupted.get());
    assertTrue(generator.blockedFinished.await(10, TimeUnit.SECONDS));
    assertNoCompletionExports(generator);
  }

  @Test
  public void successfulTasksStillRunCompletionExports() throws Exception {
    ProbeGenerator generator = generator(false, null);
    generator.release.countDown();
    generator.run();
    assertEquals(3, generator.finished.get());
    assertTrue(generator.options.updatedPopulationSnapshotPath.isFile());
    File[] metadata = new File(outputDirectory, "metadata").listFiles();
    assertTrue(metadata != null && metadata.length == 1);
    assertTrue(output.toString().contains("You've just generated"));
  }

  private void assertNoCompletionExports(ProbeGenerator generator) {
    assertFalse("failed runs must not save a successful population snapshot",
        generator.options.updatedPopulationSnapshotPath.exists());
    assertFalse("failed runs must not run post-completion metadata export",
        new File(outputDirectory, "metadata").exists());
    assertFalse(output.toString().contains("You've just generated"));
  }

  private Thread runner(ProbeGenerator generator, AtomicReference<Throwable> result,
      AtomicBoolean interrupted) {
    Thread runner = new Thread(() -> {
      try {
        generator.run();
      } catch (Throwable failure) {
        result.set(failure);
      } finally {
        interrupted.set(Thread.currentThread().isInterrupted());
      }
    });
    runner.start();
    return runner;
  }

  private ProbeGenerator generator(boolean snapshot, Throwable cause) throws Exception {
    Generator.GeneratorOptions options = new Generator.GeneratorOptions();
    options.population = 3;
    options.threadPoolSize = 2;
    options.state = "Massachusetts";
    options.city = "Bedford";
    options.seed = 1L;
    options.clinicianSeed = 1L;
    options.updatedPopulationSnapshotPath = new File(tempFolder.getRoot(), "updated.snapshot");
    if (snapshot) {
      options.initialPopulationSnapshotPath = tempFolder.newFile("initial.snapshot");
      try (ObjectOutputStream stream = new ObjectOutputStream(
          new FileOutputStream(options.initialPopulationSnapshotPath))) {
        stream.writeObject(Arrays.asList(new Person(1L), new Person(2L), new Person(3L)));
      }
    }
    return new ProbeGenerator(options, cause);
  }

  private static class ProbeGenerator extends Generator {
    private final Throwable cause;
    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch blockedFinished = new CountDownLatch(1);
    private final AtomicInteger finished = new AtomicInteger();

    ProbeGenerator(GeneratorOptions options, Throwable cause) {
      super(options);
      this.cause = cause;
    }

    @Override
    public Person generatePerson(int index, long seed) {
      return process(new Person(seed), index);
    }

    @Override
    public Person updateRecordExportPerson(Person person, int index) {
      return process(person, index);
    }

    private Person process(Person person, int index) {
      try {
        if (index == 0 && cause != null) {
          if (cause instanceof Error) {
            throw (Error) cause;
          }
          throw (RuntimeException) cause;
        }
        if (index == 1) {
          blocked.countDown();
          try {
            release.await();
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
        }
        return person;
      } finally {
        finished.incrementAndGet();
        if (index == 1) {
          blockedFinished.countDown();
        }
      }
    }
  }
}
