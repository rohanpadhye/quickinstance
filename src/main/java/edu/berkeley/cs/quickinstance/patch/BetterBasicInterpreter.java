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

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import static org.objectweb.asm.Type.*;

/**
 * Extension of {@link BasicInterpreter} that propagates actual type
 * descriptors for reference and array types.
 *
 * This eliminates the use of the generic {@link BasicValue#REFERENCE_VALUE}.
 *
 * @author Rohan Padhye
 */
public class BetterBasicInterpreter extends BasicInterpreter {

    @Override
    public BasicValue newValue(final Type type) {
        if (type != null && (type.getSort() == OBJECT || type.getSort() == ARRAY)) {
            return new BasicValue(type);
        } else {
            return super.newValue(type);
        }
    }

    @Override
    public BasicValue binaryOperation(final AbstractInsnNode insn,
                                      final BasicValue value1, final BasicValue value2)
            throws AnalyzerException {
        if (insn.getOpcode() == AALOAD) {
            assert (value1.getType().getSort() == ARRAY);
            return new BasicValue(value1.getType().getElementType());
        } else {
            return super.binaryOperation(insn, value1, value2);
        }
    }


    @Override
    public BasicValue merge(final BasicValue v, final BasicValue w) {
        // Handle trivial merge first
        if (v.equals(w)) {
            return v;
        }

        // Now try more obscure cases
        if (v == BasicValue.UNINITIALIZED_VALUE || w == BasicValue.UNINITIALIZED_VALUE) {
            // If either side is uninitialized, then so is the result
            return BasicValue.UNINITIALIZED_VALUE;
        }

        // Extract type info of both operands
        int sort1 = v.getType().getSort();
        int sort2 = w.getType().getSort();

        // If either of the types are reference types, try to do a type-hierarchy merge
        if ((sort1 == ARRAY || sort1 == OBJECT) && (sort2 == ARRAY || sort2 == OBJECT)) {

            /*
             * Alright, time to deal with some weird stuff.
             *
             * The java.lang.Class representation for arrays and objects is different: for
             * arrays the descriptor-style syntax is used (e.g. "[Ljava.lang.Integer;" or "[Z"),
             * while for objects the FQN syntax is used (e.g. "java.lang.Integer").
             *
             * Therefore, we choose the string representation for the two types based on
             * whether they represent arrays or classes, and pass these on to getCommonSuperClass(),
             * which internally invokes Class.forName() with these strings.
             */


            String type1 = sort1 == ARRAY ? v.getType().getDescriptor() : v.getType().getInternalName();
            String type2 = sort2 == ARRAY ? w.getType().getDescriptor() : w.getType().getInternalName();

            // null is a member of any type, so merge always returns the other operand
            if (type1.equals("null")) {
                return w;
            } else if (type2.equals("null")) {
                return v;
            }

            // If both types are non-array references, then compute the common super-type
            String comm = getCommonSuperClass(type1, type2);

            /*
             * Similar to above, the returned common super-class, which is the result of
             * java.lang.Class.getName(), differs on whether it uses the descriptor-style syntax
             * or the FQN syntax depending on whether the class represents an object or array.
             * Therefore, we detect the syntax and call the appropriate static method on Type
             * to construct a Type object.
             */

            return newValue(comm.startsWith("[") ? Type.getType(comm) : Type.getObjectType(comm));
        }

        // Otherwise die!
        throw new UnsupportedOperationException("Cannot merge " + v + " + " + w);
    }

    protected String getCommonSuperClass(final String type1, final String type2) {
        Class<?> c, d;
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            c = Class.forName(type1.replace('/', '.'), false, classLoader);
            d = Class.forName(type2.replace('/', '.'), false, classLoader);
        } catch (Exception e) {
            throw new RuntimeException("type1="+type1+";type2="+type2+";err="+e.toString());
        }
        if (c.isAssignableFrom(d)) {
            return type1;
        }
        if (d.isAssignableFrom(c)) {
            return type2;
        }
        if (c.isInterface() || d.isInterface()) {
            return "java/lang/Object";
        } else {
            do {
                c = c.getSuperclass();
            } while (!c.isAssignableFrom(d));
            return c.getName().replace('.', '/');
        }
    }
}
