/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.affinity;

import net.openhft.affinity.impl.NullAffinity;
import net.openhft.affinity.impl.OSXJNAAffinity;
import net.openhft.affinity.impl.PosixJNAAffinity;
import net.openhft.affinity.impl.WindowsJNAAffinity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

/**
 * Library to wrap low level JNI or JNA calls.  Can be called without needing to know the actual implementation used.
 *
 * @author peter.lawrey
 */
public enum AffinitySupport {
    ;
    @NotNull
    private static final IAffinity AFFINITY_IMPL;
    private static final Logger LOGGER = LoggerFactory.getLogger(AffinitySupport.class);
    private static Boolean JNAAvailable;

    static {
        String osName = System.getProperty("os.name");
        if (osName.contains("Win") && isWindowsJNAAffinityUsable()) {
            LOGGER.trace("Using Windows JNA-based affinity control implementation");
            AFFINITY_IMPL = WindowsJNAAffinity.INSTANCE;
        } else if (osName.contains("x") && isPosixJNAAffinityUsable()) {
            LOGGER.trace("Using Posix JNA-based affinity control implementation");
            AFFINITY_IMPL = PosixJNAAffinity.INSTANCE;
        } else if (osName.contains("Mac") && isMacJNAAffinityUsable()) {
            LOGGER.trace("Using MAC OSX JNA-based thread id implementation");
            AFFINITY_IMPL = OSXJNAAffinity.INSTANCE;
        } else {
            LOGGER.info("Using dummy affinity control implementation");
            AFFINITY_IMPL = NullAffinity.INSTANCE;
        }
    }

    private static boolean isWindowsJNAAffinityUsable() {
        if (isJNAAvailable()) {
            try {
                return WindowsJNAAffinity.LOADED;
            } catch (Throwable t) {
                logThrowable(t, "Windows JNA-based affinity not usable because it failed to load!");
                return false;
            }
        } else {
            LOGGER.warn("Windows JNA-based affinity not usable due to JNA not being available!");
            return false;
        }
    }

    private static boolean isPosixJNAAffinityUsable() {
        if (isJNAAvailable()) {
            try {
                return PosixJNAAffinity.LOADED;
            } catch (Throwable t) {
                logThrowable(t, "Posix JNA-based affinity not usable because it failed to load!");
                return false;
            }
        } else {
            LOGGER.warn("Posix JNA-based affinity not usable due to JNA not being available!");
            return false;
        }
    }

    private static boolean isMacJNAAffinityUsable() {
        if (isJNAAvailable()) {
            return true;
        } else {
            LOGGER.warn("MAX OSX JNA-based affinity not usable due to JNA not being available!");
            return false;
        }
    }

    private static void logThrowable(Throwable t, String description) {
        StringWriter sw = new StringWriter();
        sw.append(description);
        sw.append(" Reason: ");
        t.printStackTrace(new PrintWriter(sw));
        LOGGER.warn(sw.toString());
    }

    public static long getAffinity() {
        return AFFINITY_IMPL.getAffinity();
    }

    public static void setAffinity(final long affinity) {
        AFFINITY_IMPL.setAffinity(affinity);
    }

    public static int getCpu() {
        return AFFINITY_IMPL.getCpu();
    }

    public static int getThreadId() {
        return AFFINITY_IMPL.getThreadId();
    }

    public static boolean isJNAAvailable() {
        if (JNAAvailable == null)
            try {
                Class.forName("com.sun.jna.Platform");
                JNAAvailable = true;
            } catch (ClassNotFoundException ignored) {
                JNAAvailable = false;
            }
        return JNAAvailable;
    }

    public static void setThreadId() {
        try {
            int threadId = getThreadId();
            final Field tid = Thread.class.getDeclaredField("tid");
            tid.setAccessible(true);
            final Thread thread = Thread.currentThread();
            tid.setLong(thread, threadId);
            LOGGER.info("Set {} to thread id {}", thread.getName(), threadId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static AffinityLock acquireLock() {
        return isNonForkingAffinityAvailable() ? NonForkingAffinityLock.acquireLock() : AffinityLock.acquireLock();
    }

    public static AffinityLock acquireCore() {
        return isNonForkingAffinityAvailable() ? NonForkingAffinityLock.acquireCore() : AffinityLock.acquireCore();
    }

    public static AffinityLock acquireLock(boolean bind) {
        return isNonForkingAffinityAvailable() ? NonForkingAffinityLock.acquireLock(bind) : AffinityLock.acquireLock(bind);
    }

    public static AffinityLock acquireCore(boolean bind) {
        return isNonForkingAffinityAvailable() ? NonForkingAffinityLock.acquireCore(bind) : AffinityLock.acquireCore(bind);
    }

    private static boolean isNonForkingAffinityAvailable() {
        BootClassPath bootClassPath = BootClassPath.INSTANCE;
        return bootClassPath.has("java.lang.ThreadTrackingGroup") && bootClassPath.has("java.lang.ThreadLifecycleListener");
    }
}
