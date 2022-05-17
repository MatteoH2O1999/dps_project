package seta;

import taxi.*;

import java.io.IOException;
import java.util.Random;

class SETAProcess {
    public static void main(String[] args) {
        SETA seta = new SETA();
        seta.start();
        System.out.println("SETA started!!!");
        System.out.println("Press any key to stop...");
        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println("Something went wrong with your keyboard.");
            throw new RuntimeException(e);
        }
        seta.shutdown();
        try {
            seta.join();
        } catch (InterruptedException e) {
            System.out.println("Something went wrong while waiting for SETA termination.");
            throw new RuntimeException(e);
        }
    }
}

public class SETA extends Thread{
    private final static String address = "localhost";
    private final static int port = 1883;
    private final static long numberOfRidesToGenerate = 2;
    private final static long timeIntervalToGenerateInSeconds = 5;

    private final Random randomGenerator = new Random();
    private volatile boolean loop = true;

    public SETA() {
    }

    @Override
    public void run() {
        this.initializeSETA();
        while (this.loop) {
            this.sendRide(this.createRide());
            try {
                Thread.sleep(this.getNextRideInterval());
            } catch (InterruptedException e) {
                System.out.println("Thread.sleep was interrupted.");
                e.printStackTrace();
                return;
            }
        }
    }

    private void initializeSETA() {
        // TODO
    }

    private static boolean rideIsValid(TaxiRide taxiRide) {
        return Coordinate.getDistanceBetween(taxiRide.getStartCoordinate(), taxiRide.getArrivalCoordinate()) > 0;
    }

    private TaxiRide createRide() {
        TaxiRide ride;
        do {
            Coordinate startCoordinate = new Coordinate(this.randomGenerator.nextInt(10), this.randomGenerator.nextInt(10));
            Coordinate arrivalCoordinate = new Coordinate(this.randomGenerator.nextInt(10), this.randomGenerator.nextInt(10));
            ride = new TaxiRide(startCoordinate, arrivalCoordinate);
        } while (SETA.rideIsValid(ride));
        return ride;
    }

    private long getNextRideInterval() {
        long expectedTimeBetweenRides = SETA.timeIntervalToGenerateInSeconds / SETA.numberOfRidesToGenerate;
        long interval = (long)this.randomGenerator.nextGaussian() + expectedTimeBetweenRides;
        return Math.abs(interval);
    }

    private void sendRide(TaxiRide taxiRide) {
        // TODO
    }

    public void shutdown() {
        this.loop = false;
    }
}
