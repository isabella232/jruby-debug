package org.jruby.debug;

import java.util.ArrayList;
import java.util.List;
import org.jruby.RubyThread;
import org.jruby.debug.DebugContext.StopReason;
import org.jruby.runtime.builtin.IRubyObject;

final class DebugContext {

    static final String AT_BREAKPOINT = "at_breakpoint";
    static final String AT_LINE = "at_line";
    static final String AT_TRACING = "at_tracing";
    static final String LIST = "list";

    private static int thnumMax = 0;

    enum StopReason {
        NONE, STEP, BREAKPOINT, CATCHPOINT
    }

    private final RubyThread thread;
    private IRubyObject breakpoint;
    private final List<DebugFrame> frames;
    private int lastLine;
    private String lastFile;
    private int stackSize;
    private int destFrame;
    private int stopFrame;
    private int stopNext;
    private int stopLine;
    private int stackLen;
    private StopReason stopReason;
    private int thnum;
    private boolean dead;

    // flags
    private boolean suspended;
    private boolean wasRunning;
    private boolean ignored;
    private boolean skipped;
    private boolean enableBreakpoint;
    private boolean stepped;
    private boolean tracing;
    private boolean forceMove;

    DebugContext(final RubyThread thread) {
        thnum = ++thnumMax;
        lastFile = null;
        lastLine = 0;
        stopNext = -1;
        destFrame = -1;
        stopLine = -1;
        stopFrame = -1;
        stopReason = StopReason.NONE;
        frames = new ArrayList<DebugFrame>();
        stackSize = 0;
        breakpoint = thread.getRuntime().getNil();
        this.thread = thread;
    }

    
    void addFrame(final DebugFrame debugFrame) {
        frames.add(debugFrame);
    }

    RubyThread getThread() {
        return thread;
    }

    DebugFrame getFrame(int index) {
        return frames.get(index);
    }

    void increaseStackSize() {
        this.stackSize++;
    }

    void decreaseStackSize() {
        this.stackSize--;
    }

    IRubyObject getBreakpoint() {
        return breakpoint;
    }

    void setBreakpoint(IRubyObject breakpoint) {
        this.breakpoint = breakpoint;
    }

    int getDestFrame() {
        return destFrame;
    }

    void setDestFrame(int destFrame) {
        this.destFrame = destFrame;
    }

    boolean isEnableBreakpoint() {
        return enableBreakpoint;
    }

    void setEnableBreakpoint(boolean enableBreakpoint) {
        this.enableBreakpoint = enableBreakpoint;
    }

    boolean isForceMove() {
        return forceMove;
    }

    void setForceMove(boolean forceMove) {
        this.forceMove = forceMove;
    }

    boolean isIgnored() {
        return ignored;
    }

    void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    String getLastFile() {
        return lastFile;
    }

    void setLastFile(String lastFile) {
        this.lastFile = lastFile;
    }

    int getLastLine() {
        return lastLine;
    }

    void setLastLine(int lastLine) {
        this.lastLine = lastLine;
    }

    boolean isSkipped() {
        return skipped;
    }

    void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    int getStackLen() {
        return stackLen;
    }

    void setStackLen(int stackLen) {
        this.stackLen = stackLen;
    }

    int getStackSize() {
        return stackSize;
    }

    void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    boolean isStepped() {
        return stepped;
    }

    void setStepped(boolean stepped) {
        this.stepped = stepped;
    }

    int getStopFrame() {
        return stopFrame;
    }

    void setStopFrame(int stopFrame) {
        this.stopFrame = stopFrame;
    }

    int getStopLine() {
        return stopLine;
    }

    void setStopLine(int stopLine) {
        this.stopLine = stopLine;
    }

    int getStopNext() {
        return stopNext;
    }

    void setStopNext(int stopNext) {
        this.stopNext = stopNext;
    }

    StopReason getStopReason() {
        return stopReason;
    }

    void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    boolean isSuspended() {
        return suspended;
    }

    void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    int getThnum() {
        return thnum;
    }

    void setThnum(int thnum) {
        this.thnum = thnum;
    }

    static int getThnumMax() {
        return thnumMax;
    }

    static void setThnumMax(int thnumMax) {
        DebugContext.thnumMax = thnumMax;
    }

    boolean isTracing() {
        return tracing;
    }

    void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    boolean isWasRunning() {
        return wasRunning;
    }

    void setWasRunning(boolean wasRunning) {
        this.wasRunning = wasRunning;
    }

    boolean isDead() {
        return dead;
    }

    void setDead(boolean dead) {
        this.dead = dead;
    }
}
