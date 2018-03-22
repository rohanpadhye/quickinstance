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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import edu.berkeley.cs.quickinstance.SafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * @author Rohan Padhye
 */
public class ProfilingTransformer implements ClassFileTransformer {

    private static String[] banned = {"[", "java", "sun", "jdk",
            "org/objectweb/asm", "edu/berkeley/cs/quickinstance", "org/w3c"};

    public static void premain(String agentArgs, Instrumentation inst) throws ClassNotFoundException {
        Profiler.init();
        inst.addTransformer(new ProfilingTransformer(), true);
    }

    private static boolean shouldExclude(String cname) {
        for (String e : banned) {
            if (cname.startsWith(e)) {
                return true;
            }
        }
        return false;
    }

    String instDir = ".cache";

    @Override
    synchronized public byte[] transform(ClassLoader loader, String cname, Class<?> classBeingRedefined,
                                         ProtectionDomain d, byte[] cbuf)
            throws IllegalClassFormatException {

        // Do not instrument the JDK or instrumentation classes
        if (shouldExclude(cname)) {
            return null;
        }


        ClassReader cr = new ClassReader(cbuf);
        ClassWriter cw = new SafeClassWriter(cr,  loader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ProfilingClassVisitor(cw, cname);

        try {
            cr.accept(cv, 0);
        } catch (Throwable e) {
            System.err.printf("Error instrumenting class %s: %s\n", cname, e.getMessage());
            return null;
        }

        byte[] ret = cw.toByteArray();

        if (instDir != null) {
            try {
                File cachedFile = new File(instDir + "/" + cname + ".instrumented.class");
                File referenceFile = new File(instDir + "/" + cname + ".original.class");
                File parent = new File(cachedFile.getParent());
                parent.mkdirs();
                try(FileOutputStream out = new FileOutputStream(cachedFile)) {
                    out.write(ret);
                }
                try(FileOutputStream out = new FileOutputStream(referenceFile)) {
                    out.write(cbuf);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return ret;
    }

}
