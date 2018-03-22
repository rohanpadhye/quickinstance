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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Rohan Padhye
 */
public class ProfilingMethodVisitor extends MethodVisitor {

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final String superName;

    public ProfilingMethodVisitor(MethodVisitor mv, String className,
                                         String methodName, String descriptor, String superName) {
        super(ASM5, mv);
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.superName = superName;
    }

    @Override
    public void visitTypeInsn(int opcode,
                              String type) {
        if (opcode == INSTANCEOF) {
            mv.visitLdcInsn(Type.getObjectType(type));
            mv.visitMethodInsn(INVOKESTATIC, "edu/berkeley/cs/quickinstance/profile/Profiler",
                    "instanceOf", "(Ljava/lang/Object;Ljava/lang/Class;)Z", false);
        } else {
            mv.visitTypeInsn(opcode, type);
        }
    }
}
