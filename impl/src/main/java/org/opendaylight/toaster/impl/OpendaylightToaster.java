/*
 * Copyright © 2016 Yoyodyne, Inc.  and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.DisplayString;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.Toaster;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;


public class OpendaylightToaster implements AutoCloseable {
  
   private static final InstanceIdentifier<Toaster> TOASTER_IID = InstanceIdentifier.builder(Toaster.class).build();
   private static final DisplayString TOASTER_MANUFACTURER = new DisplayString("Opendaylight");
   private static final DisplayString TOASTER_MODEL_NUMBER = new DisplayString("Model 1 - Binding Aware");
    
   private DataBroker dataBroker;
    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightToaster.class);
  
   public OpendaylightToaster() {
   }
   
   public void setDataBroker(final DataBroker dataBroker) {
       this.dataBroker = dataBroker;
   }
  
   public void init() {
       setToasterStatusUp(null);
   }
 
   /**
    * Implemented from the AutoCloseable interface.
    */
   @Override
   public void close() {
       if (dataBroker != null) {
           WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
           tx.delete(OPERATIONAL,TOASTER_IID);
           Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
               @Override
               public void onSuccess( final Void result ) {
                   LOG.debug("Delete Toaster commit result: " + result);
               }
 
               @Override
               public void onFailure( final Throwable failure) {
                   LOG.error("Delete of Toaster failed", failure);
               }
           } );
       }
   }
   
   private Toaster buildToaster( Toaster.ToasterStatus status ) {
       // note - we are simulating a device whose manufacture and model are
       // fixed (embedded) into the hardware.
       // This is why the manufacture and model number are hardcoded.
       return new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURER).setToasterModelNumber(TOASTER_MODEL_NUMBER)
               .setToasterStatus( status ).build();
   }
 
   private void setToasterStatusUp( final Function<Boolean,Void> resultCallback ) {
       WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
       tx.put(OPERATIONAL,TOASTER_IID, buildToaster(Toaster.ToasterStatus.Up));

       Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
           @Override
           public void onSuccess(final Void result) {
               notifyCallback(true);
           }

           @Override
           public void onFailure(final Throwable failure) {
               // We shouldn't get an OptimisticLockFailedException (or any ex) as no
               // other component should be updating the operational state.
               LOG.error("Failed to update toaster status", failure);

               notifyCallback(false);
           }

           void notifyCallback(final boolean result) {
               if (resultCallback != null) {
                   resultCallback.apply(result);
               }
           }
       });
   }
}
