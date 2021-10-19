package net.openhft.affinity;

import net.openhft.affinity.common.ProcessRunner;
import net.openhft.affinity.lockchecker.FileLockBasedLockChecker;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static net.openhft.affinity.LockCheck.IS_LINUX;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class MultiProcessAffinityTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private String originalTmpDir;

    @Before
    public void setUp() {
        originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", folder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() {
        System.setProperty("java.io.tmpdir", originalTmpDir);
    }

    @Test
    public void shouldNotAcquireLockOnCoresLockedByOtherProcesses() throws IOException, InterruptedException {
        Assume.assumeTrue(IS_LINUX);
        // run the separate affinity locker
        final Process affinityLockerProcess = ProcessRunner.runClass(AffinityLockerProcess.class,
                new String[]{"-Djava.io.tmpdir=" + folder.getRoot().getAbsolutePath()},
                new String[]{"last"});
        try {
            int lastCpuId = AffinityLock.PROCESSORS - 1;

            // wait for the CPU to be locked
            long endTime = System.currentTimeMillis() + 5_000;
            while (FileLockBasedLockChecker.getInstance().isLockFree(lastCpuId)) {
                Thread.sleep(100);
                if (System.currentTimeMillis() > endTime) {
                    ProcessRunner.printProcessOutput("AffinityLockerProcess", affinityLockerProcess);
                    fail("Timed out waiting for the sub-process to acquire the lock");
                }
            }

            try (AffinityLock lock = AffinityLock.acquireLock("last")) {
                assertNotEquals(lastCpuId, lock.cpuId());
            }
        } finally {
            affinityLockerProcess.destroy();
            if (!affinityLockerProcess.waitFor(5, TimeUnit.SECONDS)) {
                fail("Sub-process didn't terminate!");
            }
        }
    }

    static class AffinityLockerProcess {

        private static final Logger LOGGER = LoggerFactory.getLogger(AffinityLockerProcess.class);

        public static void main(String[] args) {
            String cpuIdToLock = args[0];

            try (final AffinityLock affinityLock = AffinityLock.acquireLock(cpuIdToLock)) {
                LOGGER.info("Got affinity lock " + affinityLock);
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                // expected, just end
            }
        }
    }
}
