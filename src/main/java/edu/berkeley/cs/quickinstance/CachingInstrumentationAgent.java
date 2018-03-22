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
package edu.berkeley.cs.quickinstance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Arrays;

/**
 * @author Rohan Padhye
 */
@SuppressWarnings("unused") // Loaded by javaagent
public class CachingInstrumentationAgent implements ClassFileTransformer {

    private static final String[] banned = {"[", "java", "sun", "jdk",
            "org/objectweb/asm", "edu/berkeley/cs/quickinstance", "org/w3c"};

    private static final String instDir = System.getProperty("quickinstance.cacheDir",
            ".cache");
    private static final String transformer = System.getProperty("quickinstance.transformer",
            "edu.berkeley.cs.quickinstance.profile.ProfilingTransformer");


    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        Class<?> transformerClass = Class.forName(transformer);
        inst.addTransformer(new CachingInstrumentationAgent((ClassFileTransformer) transformerClass.newInstance()),
                true);
    }

    private static boolean shouldExclude(String cname) {
        for (String e : banned) {
            if (cname.startsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private final ClassFileTransformer delegate;
    private final String shortName;

    public CachingInstrumentationAgent(ClassFileTransformer delegate) {
        this.delegate = delegate;
        this.shortName = delegate.getClass().getSimpleName();
    }

    @Override
    synchronized public byte[] transform(ClassLoader loader, String cname, Class<?> classBeingRedefined,
                                         ProtectionDomain d, byte[] cbuf)
            throws IllegalClassFormatException {

        // Do not instrument the JDK or instrumentation classes
        if (shouldExclude(cname)) {
            return null;
        }

        File cachedFile = new File(String.format("%s/%s/%s.class", instDir, shortName, cname));
        File referenceFile = new File(String.format("%s/%s/%s.class", instDir, "original", cname));


        if (instDir != null) {
            if (cachedFile.exists() && referenceFile.exists()) {
                try {
                    byte[] origBytes = Files.readAllBytes(referenceFile.toPath());
                    if (Arrays.equals(cbuf, origBytes)) {
                        byte[] instBytes = Files.readAllBytes(cachedFile.toPath());
                        //System.err.printf("[instrument] %s found in disk-cache!\n", cname);
                        return instBytes;
                    }
                } catch (IOException e) {
                    // Ignore, go to full instrumentation
                }
            }
        }

        byte[] ret = delegate.transform(loader, cname, classBeingRedefined, d, cbuf);


        if (instDir != null) {
            try {
                cachedFile.getParentFile().mkdirs();
                referenceFile.getParentFile().mkdirs();
                try(FileOutputStream out = new FileOutputStream(cachedFile)) {
                    out.write(ret);
                }
                try(FileOutputStream out = new FileOutputStream(referenceFile)) {
                    out.write(cbuf);
                }

                //System.err.printf("[instrument] %s instrumented successfully.\n", cname);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return ret;
    }

}
