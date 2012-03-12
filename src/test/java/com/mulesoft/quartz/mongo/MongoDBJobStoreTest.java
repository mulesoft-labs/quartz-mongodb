package com.mulesoft.quartz.mongo;
import com.mongodb.DB;
import com.mongodb.Mongo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.simpl.SimpleClassLoadHelper;
import org.quartz.spi.OperableTrigger;

import java.util.Date;

import static org.quartz.SimpleScheduleBuilder.repeatMinutelyForever;
import static org.quartz.TriggerBuilder.newTrigger;

/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

public class MongoDBJobStoreTest extends Assert {

    private static final String MONGO_HOST = System.getProperty("mongo.db.host","127.0.0.1");
    private static final int MONGO_PORT = Integer.parseInt(System.getProperty("mongo.db.port","27017"));
    private static final String MONGO_DATABASE = System.getProperty("mongo.db.name","quartz");
    private static final String MONGO_USER = System.getProperty("mongo.db.user");
    private static final String MONGO_PASSWORD = System.getProperty("mongo.db.password");

    private MongoDBJobStore store;

    @Before
    public void setUpJobStore() throws Exception {
        Mongo mongo = new Mongo(MONGO_HOST, MONGO_PORT);
        DB db = mongo.getDB(MONGO_DATABASE);
        if (MONGO_USER != null) {
            db.authenticate(MONGO_USER, MONGO_PASSWORD.toCharArray());
        }
        db.getCollection("quartz_jobs").drop();
        db.getCollection("quartz_triggers").drop();
        db.getCollection("quartz_locks").drop();
        
        store = new MongoDBJobStore();
        store.setInstanceName("test");
        store.setDbName(MONGO_DATABASE);
        store.setAddresses(String.format("%s:%d",MONGO_HOST, MONGO_PORT));
        store.initialize(new SimpleClassLoadHelper(), null);
    }
    
    @Test
    public void testJobStorage() throws Exception {
        assertEquals(0, store.getJobCollection().count());
        
        JobDetail job = JobBuilder.newJob()
            .storeDurably()
            .usingJobData("key", "value")
            .withIdentity("name", "group")
            .build();
        
        store.storeJob(job, false);
        
        try {
            store.storeJob(job, false);
            fail("Expected dupliate");
        } catch (ObjectAlreadyExistsException e) {
            
        }
        
        assertEquals(1, store.getJobCollection().count());
        assertEquals(1, store.getNumberOfJobs());

        OperableTrigger trigger = (OperableTrigger)newTrigger()
            .withIdentity("name", "group")
            .forJob(job)
            .startAt(new Date())
            .withSchedule(repeatMinutelyForever())
            .build();
       
        store.storeTrigger(trigger, false);
        
        try {
            store.storeTrigger(trigger, false);
            fail("Should not be able to store twice");
        } catch (ObjectAlreadyExistsException e) {
            // expected
        }
        
        OperableTrigger trigger2 = (OperableTrigger)newTrigger()
            .withIdentity("name2", "group")
            .forJob(job)
            .startAt(new Date())
            .withSchedule(repeatMinutelyForever())
            .build();
   
        store.storeTrigger(trigger2, false);
    
        JobDetail job2 = store.retrieveJob(job.getKey());
        assertEquals("name", job2.getKey().getName());
        assertEquals("group", job2.getKey().getGroup());
        assertEquals(1, job2.getJobDataMap().size());
        assertEquals("value", job2.getJobDataMap().get("key"));
        
        trigger2 = store.retrieveTrigger(trigger.getKey());
        assertEquals("name", trigger2.getKey().getName());
        assertEquals("group", trigger2.getKey().getGroup());
   }
}
