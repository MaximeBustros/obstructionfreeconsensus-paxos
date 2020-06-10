package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;

public class Process extends UntypedAbstractActor {

	private int ballot;
    private int proposal;
    private int readballot;
    private int imposeballot;
    private int estimate;
    private long time;
    
    public Process() {
    	
    }
    
    public static Props createActor() {
        return Props.create(Process.class, () -> {
            return new Process();
        });
    }
    
    @Override
    public void onReceive(Object message) throws Throwable {
    }
}