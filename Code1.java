package org.example;

import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Philosophe extends Thread {
    private int id;
    private Semaphore leftFork;
    private Semaphore rightFork;
    private Semaphore arbitre; // Sémaphore global pour éviter la famine
    private AtomicInteger eatCount; // Compteur de repas pour ce philosophe
    private PriorityBlockingQueue<Philosophe> priorityQueue; // File de priorité
    private Lock forkLock; // Verrou pour la prise atomique des fourchettes
    private long lastEatenTime; // Dernier moment où le philosophe a mangé
    private long firstTime;
    private long relaseTime; // Dernier moment où le philosophe a mangé
    private static final long TIMEOUT = 1000; // Temps maximum sans manger (2 secondes)
    private Random random = new Random();

    public Philosophe(int id, Semaphore leftFork, Semaphore rightFork, Semaphore arbitre, AtomicInteger eatCount, PriorityBlockingQueue<Philosophe> priorityQueue, Lock forkLock,long time) {
        this.id = id;
        this.leftFork = leftFork;
        this.rightFork = rightFork;
        this.arbitre = arbitre;
        this.eatCount = eatCount;
        this.priorityQueue = priorityQueue;
        this.forkLock = forkLock;
        this.lastEatenTime = System.currentTimeMillis() -time ;
        this.firstTime=time ;// Initialiser le temps du dernier repas
    }

    public int getEatCount() {
        return eatCount.get();
    }

    public int getIdPhilosophe() {
        return id;
    }

    public long getLastEatenTime() {
        return lastEatenTime;
    }

    public static long getTIMEOUT() {
        return TIMEOUT;
    }

    private void think() throws InterruptedException {
        int thinkTime = random.nextInt(500) ; // Random time between 200 and 700 ms
        System.out.println("[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " est en train de penser pendant " + thinkTime + " ms.");
        Thread.sleep(thinkTime);
    }

    private void eat() throws InterruptedException {
        lastEatenTime = System.currentTimeMillis() ; // Réinitialiser le temps du dernier repas
        int eatTime = random.nextInt(800); // Random time between 0 and 500 ms
        System.out.println("[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " est en train de manger pendant " + eatTime + " ms.");
        Thread.sleep(eatTime);
        eatCount.set(eatCount.incrementAndGet()); // Incrémenter le compteur de repas
        lastEatenTime = System.currentTimeMillis()  ; // Réinitialiser le temps du dernier repas
    }

    @Override
    public void run() {
        try {
            while (eatCount.intValue()<5) {
                think();

                // S'inscrire dans la file de priorité
                priorityQueue.add(this);

                // Attendre que l'arbitre donne la permission
                arbitre.acquire();

                // Prendre les fourchettes de manière atomique
                forkLock.lock();
                try {
                    leftFork.acquire();
                    System.out.println("[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " a pris la fourchette gauche.");
                    rightFork.acquire();
                    System.out.println("[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " a pris la fourchette droite.");
                } finally {
                    forkLock.unlock();
                }

                eat();

                // Relâcher les fourchettes
                rightFork.release();
                relaseTime = System.currentTimeMillis() - firstTime;
                leftFork.release();
                System.out.println("[" + (relaseTime) + "ms ] Philosophe " + id + " a relâché la fourchette droite et la fourchette gauche..");

                // Libérer la permission
                arbitre.release();

                // Se retirer de la file de priorité
                priorityQueue.remove(this);
            }
            System.out.println("=========================================================\n[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " manger 5 fois.\n=========================================================");
        } catch (InterruptedException e) {
            System.out.println("[" + (System.currentTimeMillis() - firstTime) + "ms ] Philosophe " + id + " a été interrompu.");
            Thread.currentThread().interrupt();
        } finally {
            // S'assurer que les fourchettes sont relâchées en cas d'erreur
            if (leftFork.availablePermits() == 0) leftFork.release();
            if (rightFork.availablePermits() == 0) rightFork.release();
        }
    }
}

class DinerPhilosophes {
    public static void main(String[] args) {
        long time = System.currentTimeMillis() ; // Réinitialiser le temps du dernier repas
        int numPhilosophes = 5;
        Semaphore[] forks = new Semaphore[numPhilosophes];
        Philosophe[] philosophes = new Philosophe[numPhilosophes];
        AtomicInteger[] eatCount = new AtomicInteger[numPhilosophes];

        // Initialiser les sémaphores (fourchettes) et les compteurs de repas
        for (int i = 0; i < numPhilosophes; i++) {
            forks[i] = new Semaphore(1);
            eatCount[i] = new AtomicInteger(0);
        }

        // Sémaphore global pour éviter la famine
        Semaphore arbitre = new Semaphore(numPhilosophes - 1);

        // File de priorité pour gérer l'ordre des philosophes
        PriorityBlockingQueue<Philosophe> priorityQueue = new PriorityBlockingQueue<>(numPhilosophes, (p1, p2) -> {
            return Integer.compare(p1.getEatCount(), p2.getEatCount()); // Priorité au philosophe qui a mangé le moins
        });

        // Verrou pour la prise atomique des fourchettes
        Lock forkLock = new ReentrantLock();

        // Créer et démarrer les philosophes
        for (int i = 0; i < numPhilosophes; i++) {
            Semaphore leftFork = forks[i];
            Semaphore rightFork = forks[(i + 1) % numPhilosophes];
            philosophes[i] = new Philosophe(i, leftFork, rightFork, arbitre, eatCount[i], priorityQueue, forkLock,time);
            philosophes[i].start();
        }

        // Thread pour gérer l'arbitre en fonction de la file de priorité
        new Thread(() -> {
            while (true) {
                try {
                    // Attendre qu'un philosophe demande la permission
                    Philosophe nextPhilosophe = priorityQueue.take(); // Prend le philosophe qui a mangé le moins
                    arbitre.acquire(); // Donne la permission à ce philosophe

                    // Simuler un délai pour la prise de décision de l'arbitre
                    Thread.sleep(100);

                    // Libérer la permission pour le philosophe suivant
                    arbitre.release();
                } catch (InterruptedException e) {
                    System.out.println("[" +( System.currentTimeMillis() -time)  + "] Arbitre a été interrompu.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).start();

        // Thread pour surveiller les philosophes et arrêter le programme si un philosophe meurt
        new Thread(() -> {
            while (true) {
                try {
                    int count = 0;
                    Thread.sleep(10); // Vérifier toutes les 10 millisecondes
                    for (Philosophe philosophe : philosophes) {
                        if (philosophe.getEatCount()==5){
                            count++;
                        }
                        else if (((System.currentTimeMillis() -time  - philosophe.getLastEatenTime()) > Philosophe.getTIMEOUT()) ) {
                            System.out.println("[" + (System.currentTimeMillis() -time)  + "] Philosophe " + philosophe.getIdPhilosophe() + " est mort de faim. Arrêt du programme.");
                            // Interrompre tous les philosophes
                            for (Philosophe p : philosophes) {
                                p.interrupt();
                            }
                            // Arrêter le programme
                            System.exit(0);
                        }
                    }
                    if (count==5) {
                        System.out.println("====================================================\n" +
                                "||     Tous les philosophe ont manger 5 fois      ||\n" +
                                "====================================================");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.out.println("[" + (System.currentTimeMillis() -time)  + "] Surveillance des philosophes interrompue.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).start();
    }
}
