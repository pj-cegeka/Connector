/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.transferprocess.dispatcher.delegate;

import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.protocol.dsp.spi.dispatcher.DspHttpDispatcherDelegate;
import org.eclipse.edc.protocol.dsp.spi.testfixtures.dispatcher.DspHttpDispatcherDelegateTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferCompletionDelegateTest extends DspHttpDispatcherDelegateTestBase<TransferCompletionMessage> {

    private TransferCompletionDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new TransferCompletionDelegate(serializer);
    }

    @Test
    void parseResponse_returnNull() {
        testParseResponse_shouldReturnNullFunction_whenResponseBodyNotProcessed();
    }

    @Override
    protected DspHttpDispatcherDelegate<?> delegate() {
        return delegate;
    }
}
