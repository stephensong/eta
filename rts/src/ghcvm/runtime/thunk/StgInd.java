package ghcvm.runtime.closure;

import ghcvm.runtime.*;
import ghcvm.runtime.types.*;
import ghcvm.runtime.message.*;
import static ghcvm.runtime.types.StgTSO.WhyBlocked.*;

public class StgInd extends StgClosure {
    public static final StgPayload emptyPayload = new StgPayload();
    public volatile StgClosure indirectee;
    public StgPayload payload = emptyPayload;
    private static final AtomicReferenceFieldUpdater<StgInd, StgClosure> indirecteeUpdater = AtomicReferenceFieldUpdater.newUpdater(StgInd.class, StgClosure.class, "indirectee");

    public StgInd(StgClosure indirectee) {
        this.indirectee = indirectee;
    }

    public abstract void thunkEnter(StgContext context);

    @Override
    public void enter(StgContext context) {
        if (payload == null) {
            retry: do {
                if (indirectee.isEvaluated()) {
                    context.R1 = indirectee;
                } else {
                    StgTSO currentTSO = context.currentTSO;
                    MessageBlackHole msg = new MessageBlackHole(currentTSO, this);
                    boolean blocked = context.myCapability.messageBlackHole(msg);
                    if (blocked) {
                        currentTSO.whyBlocked = BlockedOnBlackHole;
                        currentTSO.blockInfo = msg;
                        context.R1 = this;
                        Stg.block_blackhole.enter(context);
                    } else {
                        continue retry;
                    }
                }
                break;
            } while (true);
        } else {
            thunkEnter(context);
        }
    }

    public void updateWithIndirection(StgClosure ret) {
        indirectee = ret;
        payload = null;
    }

    @Override
    public final boolean isEvaluated() {
         return indirectee.isEvaluated();
    }

    @Override
    public final boolean tryLock(StgClosure oldIndirectee) {
        return indirecteeUpdater.compareAndSet(this, oldIndirectee, stgWhiteHole);
    }
}