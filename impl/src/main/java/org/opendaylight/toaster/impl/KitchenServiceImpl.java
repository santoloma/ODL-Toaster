/*
 * Copyright © 2016 Yoyodyne, Inc.  and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.common.util.jmx.AbstractMXBean;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.*;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KitchenServiceImpl extends AbstractMXBean implements KitchenService, KitchenServiceRuntimeMXBean, ToasterListener {

    private static final Logger LOG = LoggerFactory.getLogger( KitchenServiceImpl.class );

    private final ToasterService toaster;

    private volatile boolean toasterOutOfBread;

    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    public KitchenServiceImpl(ToasterService toaster) {
        super("KitchenService", "toaster-consumer", null);
        this.toaster = toaster;
    }

    @Override
    public Future<RpcResult<Void>> makeBreakfast(EggsType eggsType, Class<? extends ToastType> toastType,
                                                 int toastDoneness) {
        // Call makeToast and use JdkFutureAdapters to convert the Future to a ListenableFuture, The
        // OpendaylightToaster impl already returns a ListenableFuture so the conversion is actually a no-op.

        ListenableFuture<RpcResult<Void>> makeToastFuture = JdkFutureAdapters
                .listenInPoolThread(makeToast(toastType, toastDoneness), executor);

        ListenableFuture<RpcResult<Void>> makeEggsFuture = makeEggs(eggsType);

        // Combine the 2 ListenableFutures into 1 containing a list RpcResults.

        ListenableFuture<List<RpcResult<Void>>> combinedFutures = Futures
                .allAsList(ImmutableList.of(makeToastFuture, makeEggsFuture));

        // Then transform the RpcResults into 1.

        return Futures.transform(combinedFutures,
                (AsyncFunction<List<RpcResult<Void>>, RpcResult<Void>>) results -> {
                    boolean atLeastOneSucceeded = false;
                    ImmutableList.Builder<RpcError> errorList = ImmutableList.builder();
                    for (RpcResult<Void> result : results) {
                        if (result.isSuccessful()) {
                            atLeastOneSucceeded = true;
                        }

                        if (result.getErrors() != null) {
                            errorList.addAll(result.getErrors());
                        }
                    }

                    return Futures.immediateFuture(RpcResultBuilder.<Void>status(atLeastOneSucceeded)
                            .withRpcErrors(errorList.build()).build());
                });
    }

    private ListenableFuture<RpcResult<Void>> makeEggs(EggsType eggsType) {
        return executor.submit(() -> RpcResultBuilder.<Void>success().build());
    }

    private Future<RpcResult<Void>> makeToast( Class<? extends ToastType> toastType,
                                               int toastDoneness ) {

        if (toasterOutOfBread) {
            LOG.info("We're out of toast but we can make eggs");
            return Futures.immediateFuture(RpcResultBuilder.<Void>success().withWarning(RpcError.ErrorType.APPLICATION,
                    "partial-operation", "Toaster is out of bread but we can make you eggs").build());
        }


        // Access the ToasterService to make the toast.

        MakeToastInput toastInput = new MakeToastInputBuilder().setToasterDoneness((long) toastDoneness)
                .setToasterToastType(toastType).build();

        return toaster.makeToast(toastInput);
    }

    @Override
    public Boolean makeScrambledWithWheat() {
        try {
            // This call has to block since we must return a result to the JMX client.
            RpcResult<Void> result = makeBreakfast(EggsType.SCRAMBLED, WheatBread.class, 2).get();
            if (result.isSuccessful()) {
                LOG.info("makeBreakfast succeeded");
            } else {
                LOG.warn("makeBreakfast failed: " + result.getErrors());
            }

            return result.isSuccessful();

        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("An error occurred while maing breakfast: " + e);
        }

        return Boolean.FALSE;
    }


    /**
     * Implemented from the ToasterListener interface.
     */
    @Override
    public void onToasterOutOfBread( ToasterOutOfBread notification ) {
        LOG.info( "ToasterOutOfBread notification" );
        toasterOutOfBread = true;
    }


    /**
     * Implemented from the ToasterListener interface.
     */
    @Override
    public void onToasterRestocked( ToasterRestocked notification ) {
        LOG.info( "ToasterRestocked notification - amountOfBread: " + notification.getAmountOfBread() );
        toasterOutOfBread = false;
    }

}