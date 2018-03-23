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
package edu.berkeley.cs.quickinstance.patch;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ListIterator;

import edu.berkeley.cs.quickinstance.SafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Rohan Padhye
 */
@SuppressWarnings("unused") // Instatiated dynamically
public class PatchingTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain d, byte[] cbuf) throws IllegalClassFormatException {

        try {
            // First, read original class file into a class node using a reader
            ClassReader cr = new ClassReader(cbuf);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // Second, perform transformations on the class node
            this.runOn(cn);

            // Third, write the class node to a new class file using a writer
            ClassWriter cw = new SafeClassWriter(loader,
                    ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.printf("[instrument] %s could not be instrumented: %s\n", className, e.getMessage());
            e.printStackTrace();
            return null;
        }

    }

    public void runOn(ClassNode classNode) throws AnalyzerException {
        // Add unique type ID TODO: Generate fresh type ID
        classNode.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                "__typeID__", "J", null, Long.valueOf(1)));

        // Go through all methods with code
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.instructions.size() > 0) {
                // First, analyze all frames so that we know types, etc
                Analyzer<BasicValue> a = new Analyzer<>(new BetterBasicInterpreter());
                a.analyze(classNode.name, methodNode);
                Frame<BasicValue>[] frames = a.getFrames();

                // Then, look for instanceof instructions
                ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
                while (it.hasNext()) {
                    int idx = it.nextIndex();
                    AbstractInsnNode insn = it.next();
                    if (insn.getOpcode() == INSTANCEOF) { // ohh, the irony
                        TypeInsnNode typeInsn = ((TypeInsnNode) insn);
//                        System.out.printf("instanceof in method %s#%s: (%s, %s)\n  -- Frame: %s\n",
//                                classNode.name, methodNode.name,
//                                getTopOfStack(frames[idx]), typeInsn.desc, frames[idx]);
                    }
                }

            }
        }
    }

    private String getTopOfStack(Frame<BasicValue> frame) {
        int topIdx = frame.getStackSize() - 1;
        BasicValue value = frame.getStack(topIdx);
        Type type = value.getType();
        return type.getClassName();
    }
}
