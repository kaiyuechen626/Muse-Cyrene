// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.SuspendLambda;

/** Adapter for the shipped scanner's R8-strengthened SuspendLambda parameter. */
public final class QrContinuation extends SuspendLambda {
    public QrContinuation(Continuation<Object> completion) { super(1, completion); }
    @Override protected Object invokeSuspend(Object result) { return result; }
}
