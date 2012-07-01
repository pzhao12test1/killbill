/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.analytics;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class TestBusinessSubscriptionTransitionRecorder extends AnalyticsTestSuite {
    @Test(groups = "fast")
    public void testCreateAddOn() throws Exception {
        final UUID externalKey = UUID.randomUUID();

        // Setup the catalog
        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(Mockito.mock(Catalog.class));

        // Setup the dao
        final BusinessSubscriptionTransitionSqlDao sqlDao = new MockBusinessSubscriptionTransitionSqlDao();
        // Add a previous subscription to make sure it doesn't impact the addon
        final BusinessSubscription nextPrevSubscription = new BusinessSubscription(UUID.randomUUID().toString(),
                                                                                   UUID.randomUUID().toString(),
                                                                                   UUID.randomUUID().toString(),
                                                                                   Currency.USD,
                                                                                   new DateTime(DateTimeZone.UTC),
                                                                                   Subscription.SubscriptionState.ACTIVE,
                                                                                   UUID.randomUUID(),
                                                                                   UUID.randomUUID(),
                                                                                   catalogService.getFullCatalog());
        sqlDao.createTransition(new BusinessSubscriptionTransition(10L,
                                                                externalKey.toString(),
                                                                UUID.randomUUID().toString(),
                                                                new DateTime(DateTimeZone.UTC),
                                                                BusinessSubscriptionEvent.valueOf("ADD_MISC"),
                                                                null,
                                                                nextPrevSubscription));

        // Setup the entitlement API
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getKey()).thenReturn(externalKey.toString());
        final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
        Mockito.when(entitlementApi.getBundleFromId(Mockito.<UUID>any())).thenReturn(bundle);

        // Setup the account API
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getExternalKey()).thenReturn(externalKey.toString());
        final AccountUserApi accountApi = Mockito.mock(AccountUserApi.class);
        Mockito.when(accountApi.getAccountById(bundle.getAccountId())).thenReturn(account);

        final BusinessSubscriptionTransitionRecorder recorder = new BusinessSubscriptionTransitionRecorder(sqlDao, catalogService, entitlementApi, accountApi);

        // Create an new subscription event
        final EffectiveSubscriptionEvent eventEffective = Mockito.mock(EffectiveSubscriptionEvent.class);
        Mockito.when(eventEffective.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(eventEffective.getRequestedTransitionTime()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(eventEffective.getNextPlan()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(eventEffective.getEffectiveTransitionTime()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(eventEffective.getSubscriptionStartDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        recorder.subscriptionCreated(eventEffective);

        Assert.assertEquals(sqlDao.getTransitions(externalKey.toString()).size(), 2);
        final BusinessSubscriptionTransition transition = sqlDao.getTransitions(externalKey.toString()).get(1);
        Assert.assertEquals(transition.getTotalOrdering(), (long) eventEffective.getTotalOrdering());
        Assert.assertEquals(transition.getAccountKey(), externalKey.toString());
        // Make sure all the prev_ columns are null
        Assert.assertNull(transition.getPreviousSubscription());
    }
}
