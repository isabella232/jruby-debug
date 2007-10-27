/*
 * header & license
 * Copyright (c) 2007 Martin Krauskopf
 * Copyright (c) 2007 Peter Brant
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.jruby.debug;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyString;
import org.jruby.RubyThread;
import org.jruby.debug.DebugBreakpoint.Type;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

final class Debugger {

    private DebugEventHook debugEventHook;
    
    private Map<RubyThread, IRubyObject> threadsTable;
    
    private IRubyObject breakpoints;
    private IRubyObject catchpoint;
    private String catchpointAsString;
    private boolean tracing;
    private boolean postMortem;
    private boolean keepFrameBinding;
    private boolean debug;
    private boolean trackFrameArgs;

    private IRubyObject lastContext;
    private IRubyObject lastThread;

    private boolean started;
    private int startCount;
    private int bkp_count;

    private DebugContext lastDebugContext;

    IRubyObject start(IRubyObject recv, Block block) {
        Ruby runtime = recv.getRuntime();
        
        startCount++;
        IRubyObject result;
        if (started) {
            result = runtime.getFalse();
        } else {
            IRubyObject nil = runtime.getNil();
            lastThread  = nil;
            started = true;
            setLastContext(runtime, nil);
            tracing = false;
            postMortem = false;
            keepFrameBinding = false;
            debug = false;
            trackFrameArgs = false;
            catchpoint = nil;
            catchpointAsString = null;
            debugEventHook = new DebugEventHook(this, runtime);
            breakpoints = runtime.newArray();
            threadsTable = new IdentityHashMap<RubyThread, IRubyObject>();
            runtime.addEventHook(debugEventHook);
            result = runtime.getTrue();
        }
        
        if (block.isGiven()) {
            try {
                return block.yield(runtime.getCurrentContext(), recv);
            } finally {
                stop(runtime);
            }
        }
        
        return result;
    }

    boolean stop(final Ruby runtime) {
        checkStarted(runtime);
        startCount--;
        if (startCount > 0) {
            return false;
        }
        runtime.removeEventHook(debugEventHook);
        breakpoints = null;
        debugEventHook = null;
        started = false;
        threadsTable = null;
        catchpoint = null;
        catchpointAsString = null;
        return true;
    }

    void load(IRubyObject recv, IRubyObject[] args) {
        Ruby rt = recv.getRuntime();
        Arity.checkArgumentCount(rt, args, 1, 2);
        IRubyObject file = args[0];
        IRubyObject stop;
        if (args.length == 1) {
            stop = rt.getFalse();
        } else {
            stop = args[1];
        }

        start(recv, Block.NULL_BLOCK);
        IRubyObject context = getCurrentContext(recv);
        DebugContext debugContext = (DebugContext) context.dataGetStruct();
        if (stop.isTrue()) {
            debugContext.setStopNext(1);
        }
        rt.getLoadService().load(((RubyString) file).toString());
        stop(rt);
    }
    
    IRubyObject getCurrentContext(IRubyObject recv) {
        checkStarted(recv.getRuntime());
        RubyThread thread = recv.getRuntime().getCurrentContext().getThread();
        return threadContextLookup(thread, false).context;
    }
    
    DebugContext getCurrentDebugContext(IRubyObject recv) {
        checkStarted(recv.getRuntime());
        RubyThread thread = recv.getRuntime().getCurrentContext().getThread();
        return threadContextLookup(thread, true).debugContext;
    }

    DebugContextPair threadContextLookup(final RubyThread thread, final boolean wantDebugContext) {
        Ruby rt = thread.getRuntime();
        checkStarted(rt);

        DebugContextPair ctxs = new DebugContextPair();
        if (lastThread == thread && !lastContext.isNil()) {
            ctxs.context = lastContext;
            if (wantDebugContext) {
                ctxs.debugContext = lastDebugContext;
            }
            return ctxs;
        }

        Map<RubyThread, IRubyObject> threadsTable = this.threadsTable;
        synchronized (threadsTable) {
            ctxs.context = threadsTable.get(thread);
            if (ctxs.context == null) {
                ctxs.context = debugContextCreate(thread);
                threadsTable.put(thread, ctxs.context);
            }
        }

        DebugContext lDebugContext = (DebugContext) ctxs.context.dataGetStruct();
        if (wantDebugContext) {
            ctxs.debugContext = lDebugContext;
        }

        lastThread = thread;
        setLastContext(rt, ctxs.context);
        lastDebugContext = lDebugContext;
        return ctxs;
    }

    void checkStarted(final Ruby runtime) {
        if (!started) {
            throw runtime.newRuntimeError("Debugger.start is not called yet.");
        }
    }

    private IRubyObject debugContextCreate(RubyThread thread) {
        DebugContext debugContext = new DebugContext(thread);
        // if (thread.getType() == thread.getRuntime().getClass(DebuggerDef.DEBUG_THREAD_NAME)) {
        if (thread.getType().getName().equals("Debugger::" + RubyDebugger.DEBUG_THREAD_NAME)) {
            debugContext.setIgnored(true);
        }
        RubyClass cContext = thread.getRuntime().getModule("Debugger").getClass("Context");
        IRubyObject context = cContext.allocate();
        context.dataWrapStruct(debugContext);
        return context;
    }

    IRubyObject getDebugContexts(IRubyObject self) {
        Ruby rt = self.getRuntime();
        checkStarted(rt);
        RubyArray newList = rt.newArray();
        RubyArray list = RubyThread.list(self);

        
        synchronized (threadsTable) {
            for (int i = 0; i < list.size(); i++) {
                RubyThread thread = (RubyThread) list.entry(i);
                IRubyObject context = threadContextLookup(thread, false).context;
                newList.add(context);
            }
            Map<RubyThread, IRubyObject> threadsTable = this.threadsTable;
            for (int i = 0; i < newList.size(); i++) {
                IRubyObject context = newList.entry(i);
                DebugContext debugContext = (DebugContext) context.dataGetStruct();
                threadsTable.put(debugContext.getThread(), context);
            }
        }

        return newList;
    }
    
    void suspend(IRubyObject recv) {
        RubyArray contexts; 
        Context current;   
        
        synchronized (threadsTable) {
            contexts = (RubyArray)getDebugContexts(recv);
            current = (Context)threadContextLookup(
                    recv.getRuntime().getCurrentContext().getThread(),
                    false).context;
        }
        
        int len = contexts.getLength();
        for (int i = 0; i < len; i++) {
            Context context = (Context)contexts.get(i);
            if (context == current) {
                continue;
            }
            
            context.suspend0();
        }
    }
    
    void resume(IRubyObject recv) {
        RubyArray contexts; 
        Context current;   
        
        synchronized (threadsTable) {
            contexts = (RubyArray)getDebugContexts(recv);
            current = (Context)threadContextLookup(
                    recv.getRuntime().getCurrentContext().getThread(),
                    false).context;
        }
        
        int len = contexts.getLength();
        for (int i = 0; i < len; i++) {
            Context context = (Context)contexts.get(i);
            if (context == current) {
                continue;
            }
            
            context.resume0();
        }
    }    

    boolean isStarted() {
        return started;
    }

    void setTracing(boolean tracing) {
        this.tracing = tracing;
    }
    
    boolean isTracing() {
        return tracing;
    }

    void setKeepFrameBinding(boolean keepFrameBinding) {
        this.keepFrameBinding = keepFrameBinding;
    }

    boolean isKeepFrameBinding() {
        return keepFrameBinding;
    }

    boolean isTrackFrameArgs() {
        return trackFrameArgs;
    }

    IRubyObject getBreakpoints() {
        return breakpoints;
    }
    
    IRubyObject addBreakpoint(IRubyObject recv, IRubyObject[] args) {
        Ruby rt = recv.getRuntime();
        checkStarted(rt);
        IRubyObject result = createBreakpointFromArgs(recv, args, ++bkp_count);
        ((RubyArray) breakpoints).add(result);
        return result;
    }

    IRubyObject removeBreakpoint(IRubyObject recv, IRubyObject breakpointId) {
        int id = RubyFixnum.fix2int(breakpointId);
        RubyArray breakpointsA = ((RubyArray) breakpoints);
        for(int i = 0; i < breakpointsA.size(); i++) {
            IRubyObject breakpoint = breakpointsA.entry(i);
            DebugBreakpoint debugBreakpoint = (DebugBreakpoint) breakpoint.dataGetStruct();
            if(debugBreakpoint.getId() == id) {
                breakpointsA.remove(i);
                return breakpoint;
            }
        }
        return Util.nil(recv);
    }
    
    IRubyObject createBreakpointFromArgs(IRubyObject recv, IRubyObject[] args) {
        return createBreakpointFromArgs(recv, args, ++bkp_count);
    }

    private IRubyObject createBreakpointFromArgs(IRubyObject recv, IRubyObject[] args, int id) {
        Ruby rt = recv.getRuntime();

        IRubyObject expr;
        if (Arity.checkArgumentCount(rt, args, 2, 3) == 3) {
            expr = args[2];
        } else {
            expr = rt.getNil();
        }
        IRubyObject source = args[0];
        IRubyObject pos = args[1];
        
        Type type = pos instanceof RubyFixnum ? DebugBreakpoint.Type.POS : DebugBreakpoint.Type.METHOD;
        if (type == DebugBreakpoint.Type.POS) {
            source = source.asString();
        } else {
            pos = pos.asString();
        }
        DebugBreakpoint debugBreakpoint = new DebugBreakpoint();
        debugBreakpoint.setId(id);
        debugBreakpoint.setSource(source);
        debugBreakpoint.setType(type);
        if (type == DebugBreakpoint.Type.POS) {
            debugBreakpoint.getPos().setLine(RubyFixnum.num2int(pos));
        } else {
            debugBreakpoint.getPos().setMethodName(((RubyString) pos).toString());
        }
        debugBreakpoint.setExpr(expr.isNil() ? expr : (RubyString) expr);
        debugBreakpoint.setHitCount(0);
        debugBreakpoint.setHitValue(0);
        debugBreakpoint.setHitCondition(DebugBreakpoint.HitCondition.NONE);
        RubyClass cBreakpoint = rt.getModule("Debugger").getClass("Breakpoint");
        IRubyObject breakpoint = cBreakpoint.allocate();
        breakpoint.dataWrapStruct(debugBreakpoint);
        return breakpoint;
    }

    IRubyObject lastInterrupted(IRubyObject recv) {
        checkStarted(recv.getRuntime());
        IRubyObject result = Util.nil(recv);
        Map<RubyThread, IRubyObject> threadsTable = this.threadsTable;
        synchronized (threadsTable) {
            for (Map.Entry<RubyThread, IRubyObject> entry : threadsTable.entrySet()) {
                IRubyObject context = entry.getValue();
                DebugContext debugContext = (DebugContext) context.dataGetStruct();
                if (debugContext.getThnum() == debugEventHook.getLastDebuggedThnum()) {
                    result = context;
                    break;
                }
            }
        }
        return result;
    }
    

    void checkThreadContexts(Ruby runtime) {
        Map<RubyThread, IRubyObject> threadsTable = this.threadsTable;
        synchronized (threadsTable) {
            for (Iterator<Map.Entry<RubyThread, IRubyObject>> it = threadsTable.entrySet().iterator(); it.hasNext();) {
                Map.Entry<RubyThread, IRubyObject> entry = it.next();
                if (runtime.getFalse().eql(entry.getKey().alive_p())) {
                    it.remove();
                }
            }
        }
    }    
    
    IRubyObject skip(IRubyObject recv, Block block) {
        if (! block.isGiven()) {
            throw recv.getRuntime().newArgumentError("called without a block");
        }
        
        DebugContext context = getCurrentDebugContext(recv);
        try {
            context.setSkipped(true);
            return block.yield(recv.getRuntime().getCurrentContext(), recv.getRuntime().getNil());
        } finally {
            context.setSkipped(false);
        }
    }

    boolean isPostMortem() {
        return postMortem;
    }

    void setPostMortem(boolean postMortem) {
        this.postMortem = postMortem;
    }

    boolean isDebug() {
        return debug;
    }

    void setDebug(boolean debug) {
        this.debug = debug;
    }

    /** TODO: Get rid of me - here because of hard rewrite from C. */
    static final class DebugContextPair {
        IRubyObject context;
        DebugContext debugContext;
    }

    private void setLastContext(Ruby runtime, IRubyObject value) {
        lastContext = value;
    }

    void setTrackFrameArgs(boolean trackFrameArgs) {
        this.trackFrameArgs = trackFrameArgs;
    }
    
    IRubyObject getCatchpoint() {
        return catchpoint;
    }
    
    String getCatchpointAsString() {
        return catchpointAsString;
    }
    
    void setCatchpoint(IRubyObject recv, IRubyObject catchpoint) {
        if (catchpoint.isNil()) {
            this.catchpoint = catchpoint;
        } else {
            if (! catchpoint.isKindOf(recv.getRuntime().getString())) {
                throw recv.getRuntime().newTypeError("value of checkpoint must be String");
            }
            
            this.catchpoint = catchpoint.dup();
            this.catchpointAsString = catchpoint.toString();
        }
    }
}
