/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.objects;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.source.SourceSection;

import org.jcodings.specific.UTF8Encoding;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.om.dsl.api.Nullable;
import org.jruby.truffle.runtime.RubyArguments;
import org.jruby.truffle.runtime.RubyCallStack;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.StringOperations;
import org.jruby.truffle.runtime.hash.Entry;
import org.jruby.truffle.runtime.layouts.Layouts;
import org.jruby.util.StringSupport;

@NodeChildren({
        @NodeChild("classToAllocate"),
        @NodeChild("values")
})
public abstract class AllocateObjectNode extends RubyNode {

    private final boolean useCallerFrame;

    public AllocateObjectNode(RubyContext context, SourceSection sourceSection) {
        this(context, sourceSection, true);
    }

    public AllocateObjectNode(RubyContext context, SourceSection sourceSection, boolean useCallerFrame) {
        super(context, sourceSection);
        this.useCallerFrame = useCallerFrame;
    }

    public DynamicObject allocate(DynamicObject classToAllocate, Object... values) {
        return executeAllocateX(classToAllocate, values);
    }

    public DynamicObject allocateHash(DynamicObject classToAllocate, Object store, int size, Entry firstInSequence, Entry lastInSequence, DynamicObject defaultBlock, Object defaultValue,
            boolean compareByIdentity) {
        return allocate(classToAllocate, store, size, firstInSequence, lastInSequence, defaultBlock, defaultValue, compareByIdentity);
    }

    public abstract DynamicObject executeAllocateX(DynamicObject classToAllocate, Object[] values);

    @Specialization(guards = {
            "cachedClassToAllocate == classToAllocate",
            "!cachedIsSingleton",
            "!isTracing()"
    }, assumptions = "getTracingAssumption()", limit = "getCacheLimit()")
    public DynamicObject allocateCached(
            DynamicObject classToAllocate,
            Object[] values,
            @Cached("classToAllocate") DynamicObject cachedClassToAllocate,
            @Cached("isSingleton(classToAllocate)") boolean cachedIsSingleton,
            @Cached("getInstanceFactory(classToAllocate)") DynamicObjectFactory factory) {
        return factory.newInstance(values);
    }

    @CompilerDirectives.TruffleBoundary
    @Specialization(
            contains = "allocateCached",
            guards = {"!isSingleton(classToAllocate)", "!isTracing()"},
            assumptions = "getTracingAssumption()")
    public DynamicObject allocateUncached(DynamicObject classToAllocate, Object[] values) {
        return getInstanceFactory(classToAllocate).newInstance(values);
    }

    @CompilerDirectives.TruffleBoundary
    @Specialization(guards = {"!isSingleton(classToAllocate)", "isTracing()"},
                    assumptions = "getTracingAssumption()")
    public DynamicObject allocateTracing(DynamicObject classToAllocate, Object[] values) {
        final DynamicObject object = getInstanceFactory(classToAllocate).newInstance(values);

        final FrameInstance allocatingFrameInstance;
        final Node allocatingNode;

        if (useCallerFrame) {
            allocatingFrameInstance = RubyCallStack.getCallerFrame(getContext());
            allocatingNode = RubyCallStack.getTopMostUserCallNode();
        } else {
            allocatingFrameInstance = Truffle.getRuntime().getCurrentFrame();
            allocatingNode = this;
        }

        final Frame allocatingFrame = allocatingFrameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY, true);

        final Object allocatingSelf = RubyArguments.getSelf(allocatingFrame.getArguments());
        final String allocatingMethod = RubyArguments.getMethod(allocatingFrame.getArguments()).getName();
        final SourceSection allocatingSourceSection = allocatingNode.getEncapsulatingSourceSection();
        
        getContext().getObjectSpaceManager().traceAllocation(
                object,
                string(Layouts.CLASS.getFields(getContext().getCoreLibrary().getLogicalClass(allocatingSelf)).getName()),
                getSymbol(allocatingMethod),
                string(allocatingSourceSection.getSource().getName()),
                allocatingSourceSection.getStartLine());

        return object;
    }

    private DynamicObject string(String value) {
        return createString(StringOperations.encodeByteList(value, UTF8Encoding.INSTANCE));
    }

    @Specialization(guards = "isSingleton(classToAllocate)")
    public DynamicObject allocateSingleton(DynamicObject classToAllocate, Object[] values) {
        CompilerDirectives.transferToInterpreter();
        throw new RaiseException(getContext().getCoreLibrary().typeError("can't create instance of singleton class", this));
    }

    protected Assumption getTracingAssumption() {
        return getContext().getObjectSpaceManager().getTracingAssumption();
    }

    protected boolean isTracing() {
        return getContext().getObjectSpaceManager().isTracing();
    }

    protected boolean isSingleton(DynamicObject classToAllocate) {
        return Layouts.CLASS.getIsSingleton(classToAllocate);
    }

    protected int getCacheLimit() {
        return getContext().getOptions().ALLOCATE_CLASS_CACHE;
    }

}
