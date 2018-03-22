/*
 * Copyright (c) 2018, The Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.cs.quickinstance.profile;

import java.io.PrintStream;

/**
 * @author Rohan Padhye
 */
public class Profiler {
    private Profiler() {}

    private static long successes = 0;
    private static long failures = 0;

    static void init() {
        PrintStream out = System.err;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long total = successes + failures;
            out.printf("instanceof: total=%d, success=%d (%.2f), fail=%d (%.2f)\n",
                   total,
                   successes, ((float) successes)/((float) total),
                   failures, ((float) failures)/((float) total));
        }));
    }

    private static boolean _instanceOf(Object object, Class clazz) {
        if (object == null) {
            return false;
        } else {
            return clazz.isAssignableFrom(object.getClass());
        }

    }

    public static boolean instanceOf(Object object, Class clazz) {
        final boolean success = _instanceOf(object, clazz);
        synchronized (Profiler.class) {
            if (success) {
                successes++;
            } else {
                failures++;
            }
        }
        return success;
    }
}
